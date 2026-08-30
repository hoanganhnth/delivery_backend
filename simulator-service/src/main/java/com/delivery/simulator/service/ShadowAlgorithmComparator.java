package com.delivery.simulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only candidate replay for gradual algorithm rollout. It consumes the
 * exact Match decision trace and never feeds a result back into Match.
 */
final class ShadowAlgorithmComparator {
    private static final double DEFAULT_SPEED_KM_H = 30d;
    private static final double FAIRNESS_PENALTY_MINUTES_PER_COMPLETED_DELIVERY = 0.03d;
    private final ObjectMapper objectMapper;

    ShadowAlgorithmComparator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ObjectNode compare(JsonNode trace, JsonNode scenario) {
        Map<Long, Double> speedByShipper = speedByShipper(scenario);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("algorithmId", "eta-distance");
        result.put("algorithmVersion", "v1");
        result.put("mode", "SHADOW");
        long actualSelected = trace.path("selectedShipperId").asLong(-1L);
        result.put("actualSelectedShipperId", actualSelected);
        ArrayNode scores = result.putArray("candidateScores");

        long recommended = -1L;
        double bestEtaMinutes = Double.MAX_VALUE;
        for (JsonNode candidate : trace.path("candidates")) {
            long shipperId = candidate.path("shipperId").asLong(-1L);
            double distanceKm = candidate.path("distanceKm").asDouble(Double.NaN);
            double speedKmH = speedByShipper.getOrDefault(shipperId, DEFAULT_SPEED_KM_H);
            boolean eligible = shipperId > 0 && Double.isFinite(distanceKm) && distanceKm >= 0d
                    && speedKmH > 0d && isEligible(candidate);
            double etaMinutes = eligible ? (distanceKm / speedKmH) * 60d : Double.NaN;

            ObjectNode score = scores.addObject();
            score.put("shipperId", shipperId);
            score.put("distanceKm", distanceKm);
            score.put("speedKmH", speedKmH);
            score.put("eligible", eligible);
            if (Double.isFinite(etaMinutes)) score.put("etaMinutes", etaMinutes);
            else score.putNull("etaMinutes");
            if (!eligible) score.put("exclusionReason", exclusionReason(candidate, shipperId, distanceKm, speedKmH));

            if (eligible && etaMinutes < bestEtaMinutes) {
                bestEtaMinutes = etaMinutes;
                recommended = shipperId;
            }
        }
        result.put("recommendedShipperId", recommended);
        result.put("changesSelection", recommended > 0 && recommended != actualSelected);
        result.put("sourceAlgorithmId", trace.path("algorithmId").asText("unknown"));
        result.put("sourceAlgorithmVersion", trace.path("algorithmVersion").asText("unknown"));
        return result;
    }

    List<ObjectNode> compareAll(JsonNode trace, JsonNode scenario) {
        return List.of(compare(trace, scenario), compareBalancedEta(trace, scenario));
    }

    private ObjectNode compareBalancedEta(JsonNode trace, JsonNode scenario) {
        Map<Long, Double> speedByShipper = speedByShipper(scenario);
        Map<Long, Long> completedDeliveriesByShipper = completedDeliveriesByShipper(scenario);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("algorithmId", "balanced-eta");
        result.put("algorithmVersion", "v1");
        result.put("mode", "SHADOW");
        long actualSelected = trace.path("selectedShipperId").asLong(-1L);
        result.put("actualSelectedShipperId", actualSelected);
        result.put("fairnessPenaltyMinutesPerCompletedDelivery",
                FAIRNESS_PENALTY_MINUTES_PER_COMPLETED_DELIVERY);
        ArrayNode scores = result.putArray("candidateScores");

        long recommended = -1L;
        double bestScore = Double.MAX_VALUE;
        for (JsonNode candidate : trace.path("candidates")) {
            long shipperId = candidate.path("shipperId").asLong(-1L);
            double distanceKm = candidate.path("distanceKm").asDouble(Double.NaN);
            double speedKmH = speedByShipper.getOrDefault(shipperId, DEFAULT_SPEED_KM_H);
            boolean eligible = shipperId > 0 && Double.isFinite(distanceKm) && distanceKm >= 0d
                    && speedKmH > 0d && isEligible(candidate);
            double etaMinutes = eligible ? (distanceKm / speedKmH) * 60d : Double.NaN;
            long completedDeliveries = completedDeliveriesByShipper.getOrDefault(shipperId, 0L);
            double fairnessPenalty = eligible
                    ? completedDeliveries * FAIRNESS_PENALTY_MINUTES_PER_COMPLETED_DELIVERY : Double.NaN;
            double combinedScore = eligible ? etaMinutes + fairnessPenalty : Double.NaN;

            ObjectNode score = scores.addObject();
            score.put("shipperId", shipperId);
            score.put("distanceKm", distanceKm);
            score.put("speedKmH", speedKmH);
            score.put("eligible", eligible);
            score.put("completedDeliveries", completedDeliveries);
            if (Double.isFinite(etaMinutes)) {
                score.put("etaMinutes", etaMinutes);
                score.put("fairnessPenaltyMinutes", fairnessPenalty);
                score.put("combinedScore", combinedScore);
            } else {
                score.putNull("etaMinutes");
                score.putNull("fairnessPenaltyMinutes");
                score.putNull("combinedScore");
                score.put("exclusionReason", exclusionReason(candidate, shipperId, distanceKm, speedKmH));
            }
            if (eligible && combinedScore < bestScore) {
                bestScore = combinedScore;
                recommended = shipperId;
            }
        }
        result.put("recommendedShipperId", recommended);
        result.put("changesSelection", recommended > 0 && recommended != actualSelected);
        result.put("sourceAlgorithmId", trace.path("algorithmId").asText("unknown"));
        result.put("sourceAlgorithmVersion", trace.path("algorithmVersion").asText("unknown"));
        return result;
    }

    private Map<Long, Double> speedByShipper(JsonNode scenario) {
        Map<Long, Double> result = new HashMap<>();
        for (JsonNode shipper : scenario.path("shippers")) {
            long id = shipper.path("userId").asLong(-1L);
            double speed = shipper.path("speedKmH").asDouble(DEFAULT_SPEED_KM_H);
            if (id > 0 && Double.isFinite(speed) && speed > 0d) result.put(id, speed);
        }
        return result;
    }

    private Map<Long, Long> completedDeliveriesByShipper(JsonNode scenario) {
        Map<Long, Long> result = new HashMap<>();
        for (JsonNode shipper : scenario.path("shippers")) {
            long id = shipper.path("userId").asLong(-1L);
            long completed = Math.max(0L, shipper.path("completedDeliveries").asLong(0L));
            if (id > 0) result.put(id, completed);
        }
        return result;
    }

    private boolean isEligible(JsonNode candidate) {
        if (candidate.path("online").isBoolean() && !candidate.path("online").asBoolean()) return false;
        if (candidate.path("codEligible").isBoolean() && !candidate.path("codEligible").asBoolean()) return false;
        if (candidate.path("reasons").isArray() && !candidate.path("reasons").isEmpty()) return false;
        return !"REJECTED".equalsIgnoreCase(candidate.path("state").asText());
    }

    private String exclusionReason(JsonNode candidate, long shipperId, double distanceKm, double speedKmH) {
        if (shipperId <= 0) return "MISSING_SHIPPER_ID";
        if (!Double.isFinite(distanceKm) || distanceKm < 0d) return "MISSING_DISTANCE";
        if (!Double.isFinite(speedKmH) || speedKmH <= 0d) return "INVALID_SPEED";
        if (candidate.path("reasons").isArray() && !candidate.path("reasons").isEmpty()) {
            return candidate.path("reasons").get(0).asText("MATCH_REJECTED");
        }
        if (candidate.path("online").isBoolean() && !candidate.path("online").asBoolean()) return "OFFLINE";
        if (candidate.path("codEligible").isBoolean() && !candidate.path("codEligible").asBoolean()) return "COD_INELIGIBLE";
        return candidate.path("state").asText("MATCH_REJECTED");
    }
}
