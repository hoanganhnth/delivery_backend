package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.delivery.simulator.entity.SimulationActorLease;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationCleanupRetryTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayClient gateway = mock(GatewayClient.class);
    private final SimulationLeaseService leases = mock(SimulationLeaseService.class);
    private final SimulationService service = new SimulationService(mapper, new SimulatorProperties(), gateway, null, leases);

    @AfterEach void shutdown() { service.shutdown(); }

    @Test
    void failedLeaseCleanupRetainsFenceForRetry() {
        var state = register("ABORTED");
        var lease = attach(state);
        when(leases.releaseOrQuarantine(lease.getLeaseId(), 5L))
                .thenThrow(new IllegalStateException("lease store unavailable")).thenReturn(true);

        assertThatThrownBy(() -> service.cleanup(state.getRunId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("lease store unavailable");
        assertThat(service.snapshot(state.getRunId()).get("status")).isEqualTo("ABORTED");
        assertThat(service.cleanup(state.getRunId()).get("cleaned")).isEqualTo(true);
        verify(leases, times(2)).releaseOrQuarantine(lease.getLeaseId(), 5L);
        assertThat(service.listRuns()).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void activeCleanupDoesNotReleaseActorFence() {
        var state = register("RUNNING");
        attach(state);
        assertThatThrownBy(() -> service.cleanup(state.getRunId())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(leases, gateway);
    }

    @Test
    void failedQuarantineRetainsOriginalFenceForRetry() {
        var state = register("ABORTED");
        var lease = attach(state);
        when(leases.quarantine(lease.getLeaseId(), 5L))
                .thenThrow(new IllegalStateException("lease store unavailable")).thenReturn(true);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "quarantineRunActors", state))
                .isInstanceOf(IllegalStateException.class);
        ReflectionTestUtils.invokeMethod(service, "quarantineRunActors", state);
        verify(leases, times(2)).quarantine(lease.getLeaseId(), 5L);
        verifyNoInteractions(gateway);
    }

    @Test
    void expiredRunIsAbortedButFenceIsNotReleasedByExpiryCheck() {
        var state = register("RUNNING");
        attach(state);
        ReflectionTestUtils.setField(state, "startedAt", Instant.now().minusSeconds(3600));
        service.abortExpiredRuns();
        assertThat(state.isAborted()).isTrue();
        assertThat(state.getStatus()).isEqualTo("ABORTED");
        verifyNoInteractions(leases, gateway);
    }

    @Test
    void freshRunAndExpiredTerminalRunAreNotChangedByExpiryCheck() {
        var fresh = register("RUNNING");
        var terminal = register("PASSED");
        ReflectionTestUtils.setField(terminal, "startedAt", Instant.now().minusSeconds(3600));
        service.abortExpiredRuns();
        assertThat(fresh.getStatus()).isEqualTo("RUNNING");
        assertThat(fresh.isAborted()).isFalse();
        assertThat(terminal.getStatus()).isEqualTo("PASSED");
        verifyNoInteractions(leases, gateway);
    }

    @SuppressWarnings("unchecked")
    private SimulationRunState register(String status) {
        var state = new SimulationRunState(mapper, mapper.createObjectNode());
        state.setStatus(status);
        ((Map<String, SimulationRunState>) ReflectionTestUtils.getField(service, "runs"))
                .put(state.getRunId(), state);
        return state;
    }

    @SuppressWarnings("unchecked")
    private SimulationActorLease attach(SimulationRunState state) {
        var lease = new SimulationActorLease(UUID.randomUUID(), UUID.fromString(state.getRunId()), 101L, 5L,
                Instant.now().plusSeconds(60), "ACTIVE");
        ((Map<String, List<SimulationActorLease>>) ReflectionTestUtils.getField(service, "runLeases"))
                .put(state.getRunId(), List.of(lease));
        return lease;
    }
}
