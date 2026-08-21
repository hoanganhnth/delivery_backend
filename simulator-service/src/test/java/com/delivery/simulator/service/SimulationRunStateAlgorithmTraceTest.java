package com.delivery.simulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationRunStateAlgorithmTraceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void correlatesOnlyWhenKnownOrderAndDeliveryIdentitiesAgree() {
        SimulationRunState state = new SimulationRunState(objectMapper, scenario());
        state.setOrder(101L, "CONFIRMED");
        state.setDelivery(202L, "FINDING_SHIPPER");

        assertThat(state.matchesAlgorithmTrace(trace(101, 202))).isTrue();
        assertThat(state.matchesAlgorithmTrace(trace(101, 203))).isFalse();
        assertThat(state.matchesAlgorithmTrace(trace(102, 202))).isFalse();
    }

    @Test
    void storesAnObservedTraceOnceAndDoesNotDuplicateTimelineOnKafkaReplay() {
        SimulationRunState state = new SimulationRunState(objectMapper, scenario());
        state.setOrder(101L, "CONFIRMED");
        state.setDelivery(202L, "FINDING_SHIPPER");
        ObjectNode trace = trace(101, 202);

        state.addAlgorithmTrace(trace);
        state.addAlgorithmTrace(trace);

        var snapshot = state.snapshot();
        assertThat((java.util.List<?>) snapshot.get("algorithmTraces")).hasSize(1);
        assertThat((java.util.List<?>) snapshot.get("timeline")).hasSize(1);
    }

    private ObjectNode trace(long orderId, long deliveryId) {
        ObjectNode trace = objectMapper.createObjectNode();
        trace.put("eventId", "11111111-1111-1111-1111-111111111111");
        trace.put("orderId", orderId);
        trace.put("deliveryId", deliveryId);
        trace.put("algorithmId", "nearest-cod");
        trace.put("algorithmVersion", "v1");
        trace.put("decision", "SHIPPER_SELECTED");
        trace.putArray("stages");
        trace.putArray("candidates");
        return trace;
    }

    private ObjectNode scenario() {
        ObjectNode scenario = objectMapper.createObjectNode();
        scenario.putArray("shippers");
        scenario.putArray("assertions");
        return scenario;
    }
}
