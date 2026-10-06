package com.delivery.simulator.service;

import com.delivery.simulator.entity.SimulationActorLease;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationLeaseCoordinatorTest {
    @Test
    void legacyFixtureWithoutLeaseAdapterHasNoLeaseSideEffects() {
        var coordinator = new SimulationLeaseCoordinator(null, Map.of(), Map.of());
        coordinator.heartbeat();
        coordinator.release("missing");
    }

    @Test
    void firstLostLeaseStopsRenewalOfRemainingActors() {
        var service = mock(SimulationLeaseService.class);
        var state = new SimulationRunState(new ObjectMapper(), new ObjectMapper().createObjectNode());
        state.setStatus("RUNNING");
        var first = lease(state.getRunId(), 1L);
        var second = lease(state.getRunId(), 2L);
        var coordinator = new SimulationLeaseCoordinator(service, Map.of(state.getRunId(), state),
                Map.of(state.getRunId(), List.of(first, second)));
        coordinator.heartbeat();
        assertThat(state.isAborted()).isTrue();
        verify(service).renew(first.getLeaseId(), first.getFencingToken());
        verifyNoMoreInteractions(service);
    }

    @Test
    void partiallyCompletedCleanupRetainsAllOriginalFencesUntilRetryCompletes() {
        var service = mock(SimulationLeaseService.class);
        String runId = UUID.randomUUID().toString();
        var first = lease(runId, 1L);
        var second = lease(runId, 2L);
        Map<String, List<SimulationActorLease>> leases = new ConcurrentHashMap<>();
        leases.put(runId, List.of(first, second));
        var coordinator = new SimulationLeaseCoordinator(service, Map.of(), leases);
        when(service.releaseOrQuarantine(first.getLeaseId(), 1L)).thenReturn(true, false);
        when(service.releaseOrQuarantine(second.getLeaseId(), 2L))
                .thenThrow(new IllegalStateException("outage")).thenReturn(true);

        assertThatThrownBy(() -> coordinator.release(runId)).hasMessage("outage");
        assertThat(leases.get(runId)).containsExactly(first, second);
        coordinator.release(runId);
        assertThat(leases).isEmpty();
        verify(service, times(2)).releaseOrQuarantine(first.getLeaseId(), 1L);
        verify(service, times(2)).releaseOrQuarantine(second.getLeaseId(), 2L);
        coordinator.release(runId);
        verifyNoMoreInteractions(service);
    }

    private SimulationActorLease lease(String runId, long fence) {
        return new SimulationActorLease(UUID.randomUUID(), UUID.fromString(runId), 100L + fence,
                fence, Instant.now().plusSeconds(60), "ACTIVE");
    }
}
