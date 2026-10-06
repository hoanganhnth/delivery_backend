package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.delivery.simulator.entity.SimulationActorLease;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationHeartbeatSafetyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayClient gateway = mock(GatewayClient.class);
    private final SimulationLeaseService leases = mock(SimulationLeaseService.class);
    private final SimulationService service = new SimulationService(mapper, new SimulatorProperties(), gateway, null, leases);

    @AfterEach void shutdown() { service.shutdown(); }

    @Test
    void successfulHeartbeatKeepsActiveRunAlive() {
        var state = register("RUNNING");
        var lease = attach(state.getRunId());
        when(leases.renew(lease.getLeaseId(), 5L)).thenReturn(true);
        service.heartbeatLeases();
        assertThat(state.getStatus()).isEqualTo("RUNNING");
        assertThat(state.isAborted()).isFalse();
        verify(leases).renew(lease.getLeaseId(), 5L);
        verifyNoInteractions(gateway);
    }

    @Test
    void lostLeaseAbortsRunBeforeAnyFurtherLeaseOrGatewayAction() {
        var state = register("RUNNING");
        var lease = attach(state.getRunId());
        service.heartbeatLeases();
        assertThat(state.isAborted()).isTrue();
        assertThat(state.getStatus()).isEqualTo("ABORTED");
        service.heartbeatLeases();
        verify(leases).renew(lease.getLeaseId(), 5L);
        verifyNoMoreInteractions(leases);
        verifyNoInteractions(gateway);
    }

    @Test
    void renewalExceptionFencesRunBeforePropagatingFailure() {
        var state = register("RUNNING");
        var lease = attach(state.getRunId());
        var failure = new IllegalStateException("lease database unavailable");
        when(leases.renew(lease.getLeaseId(), 5L)).thenThrow(failure);
        assertThatThrownBy(service::heartbeatLeases).isSameAs(failure);
        assertThat(state.isAborted()).isTrue();
        assertThat(state.getStatus()).isEqualTo("ABORTED");
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASSED", "PARTIAL", "FAILED", "ABORTED"})
    void terminalRunDoesNotRenewLeases(String status) {
        attach(register(status).getRunId());
        service.heartbeatLeases();
        verifyNoInteractions(leases, gateway);
    }

    @Test
    void orphanedMemoryLeaseDoesNotRenew() {
        attach(UUID.randomUUID().toString());
        service.heartbeatLeases();
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
    private SimulationActorLease attach(String runId) {
        var lease = new SimulationActorLease(UUID.randomUUID(), UUID.fromString(runId), 101L, 5L,
                Instant.now().plusSeconds(60), "ACTIVE");
        ((Map<String, List<SimulationActorLease>>) ReflectionTestUtils.getField(service, "runLeases"))
                .put(runId, List.of(lease));
        return lease;
    }
}
