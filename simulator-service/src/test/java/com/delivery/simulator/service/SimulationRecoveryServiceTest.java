package com.delivery.simulator.service;

import com.delivery.simulator.entity.SimulationRun;
import com.delivery.simulator.entity.SimulationRunJournalEntry;
import com.delivery.simulator.repository.SimulationRunJournalRepository;
import com.delivery.simulator.repository.SimulationRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class SimulationRecoveryServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void releasesFencedActorsOnlyAfterEveryPersistedDeliveryIsTerminal() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SimulationRunJournalRepository journal = mock(SimulationRunJournalRepository.class);
        GatewayClient gateway = mock(GatewayClient.class);
        SimulationActorPoolClient actors = mock(SimulationActorPoolClient.class);
        SimulationLeaseService leases = mock(SimulationLeaseService.class);
        ObjectNode scenario = managedScenario(cohortId);
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(
                new SimulationRun(runId, "ABORTED", Instant.now(), Instant.now(),
                        mapper.writeValueAsString(scenario))));
        when(journal.findByRunIdOrderByIdAsc(runId)).thenReturn(List.of(
                new SimulationRunJournalEntry(runId, Instant.now(), "GATEWAY", "delivery",
                        "{\"deliveryId\":27}")));
        when(actors.bind(any(), eq(runId), eq(cohortId))).thenAnswer(invocation -> {
            long principal = invocation.getArgument(0, Long.class);
            return new SimulationActorPoolClient.BoundActor(principal,
                    new com.delivery.identity.contracts.SimulationContext(
                            com.delivery.identity.contracts.SimulationContext.ExecutionMode.SIMULATION,
                            runId, cohortId, 20L + principal), "token-" + principal);
        });
        when(gateway.get(eq("/api/deliveries/order/27"), eq("token-11"), any()))
                .thenReturn(mapper.readTree("{\"data\":{\"status\":\"DELIVERED\"}}"));

        SimulationRecoveryService service = new SimulationRecoveryService(
                mapper, runs, journal, gateway, actors, leases);

        var result = service.reconcile(runId);

        assertThat(result.reconciled()).isTrue();
        assertThat(result.deliveryIds()).containsExactly(27L);
        verify(actors).unbind(11L, runId, 31L);
        verify(actors).unbind(12L, runId, 32L);
        verify(actors).unbind(13L, runId, 33L);
        verify(leases).releaseReconciledRun(runId);
    }

    @Test
    void keepsBindingsWhenAnyDeliveryIsStillInFlight() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SimulationRunJournalRepository journal = mock(SimulationRunJournalRepository.class);
        GatewayClient gateway = mock(GatewayClient.class);
        SimulationActorPoolClient actors = mock(SimulationActorPoolClient.class);
        ObjectNode scenario = managedScenario(cohortId);
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(new SimulationRun(
                runId, "ABORTED", Instant.now(), Instant.now(), mapper.writeValueAsString(scenario))));
        when(journal.findByRunIdOrderByIdAsc(runId)).thenReturn(List.of(new SimulationRunJournalEntry(
                runId, Instant.now(), "GATEWAY", "delivery", "{\"deliveryId\":27}")));
        when(actors.bind(any(), eq(runId), eq(cohortId))).thenAnswer(invocation -> {
            long principal = invocation.getArgument(0, Long.class);
            return new SimulationActorPoolClient.BoundActor(principal,
                    new com.delivery.identity.contracts.SimulationContext(
                            com.delivery.identity.contracts.SimulationContext.ExecutionMode.SIMULATION,
                            runId, cohortId, 40L + principal), "token-" + principal);
        });
        when(gateway.get(eq("/api/deliveries/order/27"), eq("token-11"), any()))
                .thenReturn(mapper.readTree("{\"data\":{\"status\":\"DELIVERING\"}}"));

        var result = new SimulationRecoveryService(mapper, runs, journal, gateway, actors).reconcile(runId);

        assertThat(result.reconciled()).isFalse();
        assertThat(result.deliveryStatuses()).containsEntry(27L, "DELIVERING");
        verify(actors, never()).unbind(any(), eq(runId), any(Long.class));
    }

    @Test
    void usesPrivateDeliveryLookupWhenOldJournalHasNoDeliveryIdentity() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SimulationRunJournalRepository journal = mock(SimulationRunJournalRepository.class);
        GatewayClient gateway = mock(GatewayClient.class);
        SimulationActorPoolClient actors = mock(SimulationActorPoolClient.class);
        SimulationDeliveryRecoveryClient recovery = mock(SimulationDeliveryRecoveryClient.class);
        ObjectNode scenario = managedScenario(cohortId);
        when(runs.findById(runId)).thenReturn(java.util.Optional.of(new SimulationRun(
                runId, "FAILED", Instant.now(), Instant.now(), mapper.writeValueAsString(scenario))));
        when(journal.findByRunIdOrderByIdAsc(runId)).thenReturn(List.of());
        when(recovery.findByRunId(runId)).thenReturn(List.of(
                new SimulationDeliveryRecoveryClient.DeliveryStatus(99L, 17L, "CANCELLED")));
        when(actors.bind(any(), eq(runId), eq(cohortId))).thenAnswer(invocation -> {
            long principal = invocation.getArgument(0, Long.class);
            return new SimulationActorPoolClient.BoundActor(principal,
                    new com.delivery.identity.contracts.SimulationContext(
                            com.delivery.identity.contracts.SimulationContext.ExecutionMode.SIMULATION,
                            runId, cohortId, 60L + principal), "token-" + principal);
        });

        var result = new SimulationRecoveryService(mapper, runs, journal, gateway, actors, null, recovery)
                .reconcile(runId);

        assertThat(result.reconciled()).isTrue();
        assertThat(result.deliveryStatuses()).containsEntry(99L, "CANCELLED");
        verify(gateway, never()).get(any(), any(), any());
        verify(actors).unbind(13L, runId, 73L);
    }

    private ObjectNode managedScenario(UUID cohortId) {
        ObjectNode scenario = mapper.createObjectNode().put("cohortId", cohortId.toString());
        scenario.putObject("customer").put("principalId", 11L);
        scenario.putObject("restaurant").put("ownerPrincipalId", 12L);
        scenario.putArray("shippers").addObject().put("principalId", 13L);
        return scenario;
    }
}
