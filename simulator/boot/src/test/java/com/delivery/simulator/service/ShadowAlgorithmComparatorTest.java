package com.delivery.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class ShadowAlgorithmComparatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void recommendsTheLowestEtaEligibleCandidateWithoutChangingTheActualDecision() {
        ObjectNode scenario = mapper.createObjectNode();
        ArrayNode shippers = scenario.withArray("shippers");
        shippers.addObject().put("userId", 14).put("speedKmH", 15);
        shippers.addObject().put("userId", 15).put("speedKmH", 60);
        ObjectNode trace = mapper.createObjectNode();
        trace.put("algorithmId", "nearest-cod");
        trace.put("algorithmVersion", "v1");
        trace.put("selectedShipperId", 14);
        ArrayNode candidates = trace.withArray("candidates");
        candidates.addObject().put("shipperId", 14).put("distanceKm", 0.2).put("state", "SELECTED");
        candidates.addObject().put("shipperId", 15).put("distanceKm", 0.4).put("state", "ELIGIBLE");

        var comparison = new ShadowAlgorithmComparator(mapper).compare(trace, scenario);

        assertThat(comparison.path("algorithmId").asText()).isEqualTo("eta-distance");
        assertThat(comparison.path("algorithmVersion").asText()).isEqualTo("v1");
        assertThat(comparison.path("mode").asText()).isEqualTo("SHADOW");
        assertThat(comparison.path("actualSelectedShipperId").asLong()).isEqualTo(14L);
        assertThat(comparison.path("recommendedShipperId").asLong()).isEqualTo(15L);
        assertThat(comparison.path("changesSelection").asBoolean()).isTrue();
    }

    @Test
    void excludesCandidatesWithRecordedEligibilityRejection() {
        ObjectNode scenario = mapper.createObjectNode();
        ArrayNode shippers = scenario.withArray("shippers");
        shippers.addObject().put("userId", 14).put("speedKmH", 20);
        shippers.addObject().put("userId", 15).put("speedKmH", 100);
        ObjectNode trace = mapper.createObjectNode();
        trace.put("selectedShipperId", 14);
        ArrayNode candidates = trace.withArray("candidates");
        candidates.addObject().put("shipperId", 14).put("distanceKm", 0.2).put("state", "SELECTED");
        candidates.addObject().put("shipperId", 15).put("distanceKm", 0.1)
                .put("state", "REJECTED").withArray("reasons").add("COD_INELIGIBLE");

        var comparison = new ShadowAlgorithmComparator(mapper).compare(trace, scenario);

        assertThat(comparison.path("recommendedShipperId").asLong()).isEqualTo(14L);
        assertThat(comparison.path("candidateScores").get(1).path("eligible").asBoolean()).isFalse();
    }

    @Test
    void balancesEtaAgainstConfiguredCompletedDeliveriesInASeparateShadowProfile() {
        ObjectNode scenario = mapper.createObjectNode();
        ArrayNode shippers = scenario.withArray("shippers");
        shippers.addObject().put("userId", 14).put("speedKmH", 30).put("completedDeliveries", 20);
        shippers.addObject().put("userId", 15).put("speedKmH", 30).put("completedDeliveries", 0);
        ObjectNode trace = mapper.createObjectNode();
        trace.put("selectedShipperId", 14);
        ArrayNode candidates = trace.withArray("candidates");
        candidates.addObject().put("shipperId", 14).put("distanceKm", 0.2).put("state", "SELECTED");
        candidates.addObject().put("shipperId", 15).put("distanceKm", 0.25).put("state", "ELIGIBLE");

        var comparisons = new ShadowAlgorithmComparator(mapper).compareAll(trace, scenario);
        JsonNode balanced = comparisons.stream()
                .filter(value -> "balanced-eta".equals(value.path("algorithmId").asText()))
                .findFirst().orElseThrow();

        assertThat(balanced.path("recommendedShipperId").asLong()).isEqualTo(15L);
        assertThat(balanced.path("candidateScores").get(0).path("fairnessPenaltyMinutes").asDouble())
                .isGreaterThan(0d);
    }
}
