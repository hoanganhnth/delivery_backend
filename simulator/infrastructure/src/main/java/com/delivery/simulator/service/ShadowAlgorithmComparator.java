package com.delivery.simulator.service;
import com.delivery.simulator.domain.ShadowRanking;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
/** JSON adapter for read-only domain ranking. */
final class ShadowAlgorithmComparator {
    private final ObjectMapper mapper;
    ShadowAlgorithmComparator(ObjectMapper mapper) { this.mapper = mapper; }
    ObjectNode compare(JsonNode trace,JsonNode scenario) { return compare(trace,scenario,false); }
    List<ObjectNode> compareAll(JsonNode trace,JsonNode scenario) {
        return List.of(compare(trace,scenario,false),compare(trace,scenario,true));
    }
    private ObjectNode compare(JsonNode trace,JsonNode scenario,boolean balanced) {
        List<ShadowRanking.Actor> actors = new ArrayList<>();
        for (JsonNode actor : scenario.path("shippers")) actors.add(new ShadowRanking.Actor(
                actor.path("userId").asLong(-1),actor.path("speedKmH").asDouble(30d),
                actor.path("completedDeliveries").asLong(0)));
        List<ShadowRanking.Candidate> candidates = new ArrayList<>();
        for (JsonNode candidate : trace.path("candidates")) {
            boolean reasons = candidate.path("reasons").isArray() && !candidate.path("reasons").isEmpty();
            candidates.add(new ShadowRanking.Candidate(candidate.path("shipperId").asLong(-1),
                    candidate.path("distanceKm").asDouble(Double.NaN),
                    candidate.path("online").isBoolean() && !candidate.path("online").asBoolean(),
                    candidate.path("codEligible").isBoolean() && !candidate.path("codEligible").asBoolean(),
                    reasons,reasons ? candidate.path("reasons").get(0).asText("MATCH_REJECTED") : "",
                    candidate.path("state").asText(),candidate.path("state").asText("MATCH_REJECTED")));
        }
        var ranking = ShadowRanking.rank(candidates,actors,balanced);
        ObjectNode result = mapper.createObjectNode();
        result.put("algorithmId",balanced ? "balanced-eta" : "eta-distance");
        result.put("algorithmVersion","v1"); result.put("mode","SHADOW");
        long actual = trace.path("selectedShipperId").asLong(-1L);
        result.put("actualSelectedShipperId",actual);
        if (balanced) result.put("fairnessPenaltyMinutesPerCompletedDelivery",ShadowRanking.FAIRNESS_PENALTY);
        ArrayNode scores = result.putArray("candidateScores");
        for (var value : ranking.scores()) {
            ObjectNode score = scores.addObject(); score.put("shipperId",value.id()); score.put("distanceKm",value.distance());
            score.put("speedKmH",value.speed()); score.put("eligible",value.eligible());
            if (balanced) score.put("completedDeliveries",value.completed());
            if (Double.isFinite(value.eta())) {
                score.put("etaMinutes",value.eta());
                if (balanced) { score.put("fairnessPenaltyMinutes",value.fairness()); score.put("combinedScore",value.combined()); }
            } else {
                score.putNull("etaMinutes");
                if (balanced) { score.putNull("fairnessPenaltyMinutes"); score.putNull("combinedScore");
                    score.put("exclusionReason",exclusion(value)); }
            }
            if (!balanced && !value.eligible()) score.put("exclusionReason",exclusion(value));
        }
        result.put("recommendedShipperId",ranking.recommended());
        result.put("changesSelection",ranking.recommended() > 0 && ranking.recommended() != actual);
        result.put("sourceAlgorithmId",trace.path("algorithmId").asText("unknown"));
        result.put("sourceAlgorithmVersion",trace.path("algorithmVersion").asText("unknown"));
        return result;
    }
    private String exclusion(ShadowRanking.Score value) { return value.exclusionReason(); }
}
