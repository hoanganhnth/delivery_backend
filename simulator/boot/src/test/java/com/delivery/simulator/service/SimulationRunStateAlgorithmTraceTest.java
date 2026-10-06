package com.delivery.simulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SimulationRunStateAlgorithmTraceTest {

    @Test
    void retainsEarlierOrderIdentitySoDelayedTraceStillAttachesDuringMultiOrderRun() {
        ObjectMapper mapper = new ObjectMapper();
        SimulationRunState state = new SimulationRunState(mapper, mapper.createObjectNode());
        state.setOrder(101L, "PENDING");
        state.setDelivery(201L, "FINDING_SHIPPER");
        state.beginNextOrder(2);
        state.setOrder(102L, "PENDING");

        ObjectNode delayedTrace = mapper.createObjectNode();
        delayedTrace.put("eventId", UUID.randomUUID().toString());
        delayedTrace.put("orderId", 101L);
        delayedTrace.put("deliveryId", 201L);

        assertThat(state.matchesAlgorithmTrace(delayedTrace)).isTrue();
        assertThat((java.util.List<?>) state.snapshot().get("orders")).hasSize(2);
    }

    @Test
    void keepsActorFencedWhenTheCurrentDeliveryIsNotTerminal() {
        SimulationRunState state = new SimulationRunState(objectMapper, scenario());
        state.setDelivery(202L, "PICKED_UP");

        assertThat(state.isActorReleaseSafe()).isFalse();

        state.setDeliveryStatus("DELIVERED");
        assertThat(state.isActorReleaseSafe()).isTrue();
    }

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

    @Test
    void exposesShadowComparisonsAlongsideTheirSourceTrace() {
        SimulationRunState state = new SimulationRunState(objectMapper, scenario());
        ObjectNode comparison = objectMapper.createObjectNode();
        comparison.put("algorithmId", "eta-distance");
        comparison.put("recommendedShipperId", 15L);

        state.addAlgorithmComparison(comparison);

        var comparisons = (java.util.List<?>) state.snapshot().get("algorithmComparisons");
        assertThat(comparisons).hasSize(1);
        assertThat(objectMapper.valueToTree(comparisons).toString()).contains("eta-distance", "15");
    }

    @Test
    void exposesAUuidRunIdThatCanBePersistedAsTheRunPrimaryKey() {
        SimulationRunState state = new SimulationRunState(objectMapper, scenario());

        assertThatCode(() -> UUID.fromString(state.getRunId())).doesNotThrowAnyException();
    }

    @Test
    void emitsEveryAssertionStateChangeToTheDurableObserver() {
        SimulationRunState state = new SimulationRunState(objectMapper, assertionScenario());
        AtomicReference<java.util.Map<String, Object>> observed = new AtomicReference<>();
        state.setAssertionObserver(observed::set);

        state.assertion("assertion-1", "PASSED", "DELIVERED");

        assertThat(observed.get()).containsEntry("assertionId", "assertion-1")
                .containsEntry("status", "PASSED").containsEntry("actualValue", "DELIVERED");
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

    private ObjectNode assertionScenario() {
        ObjectNode scenario = scenario();
        scenario.withArray("assertions").addObject().put("id", "assertion-1")
                .put("expectedTerminalState", "DELIVERED");
        return scenario;
    }
}
