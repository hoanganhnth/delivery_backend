package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationRunControlTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayClient gateway = mock(GatewayClient.class);
    private final SimulationService service = new SimulationService(mapper, new SimulatorProperties(), gateway);

    @AfterEach
    void shutdown() { service.shutdown(); }

    @Test
    void pauseResumeAbortChangesControlStateWithoutGatewayActions() {
        var state = register("RUNNING");
        assertThat(service.pause(state.getRunId()).get("status")).isEqualTo("PAUSED");
        assertThat(state.isPaused()).isTrue();
        assertThat(service.resume(state.getRunId()).get("status")).isEqualTo("RUNNING");
        assertThat(state.isPaused()).isFalse();
        assertThat(service.abort(state.getRunId()).get("status")).isEqualTo("ABORTED");
        assertThat(state.isAborted()).isTrue();
        assertThat(state.isPaused()).isFalse();
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASSED", "PARTIAL", "FAILED", "ABORTED"})
    void terminalRunsCannotBeReactivatedByControls(String status) {
        var state = register(status);
        assertThat(service.pause(state.getRunId()).get("status")).isEqualTo(status);
        assertThat(service.resume(state.getRunId()).get("status")).isEqualTo(status);
        assertThat(service.abort(state.getRunId()).get("status")).isEqualTo(status);
        verifyNoInteractions(gateway);
    }

    @Test
    void activeRunCannotBeCleanedUp() {
        var state = register("RUNNING");
        assertThatThrownBy(() -> service.cleanup(state.getRunId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("terminal");
        assertThat(service.snapshot(state.getRunId()).get("status")).isEqualTo("RUNNING");
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASSED", "PARTIAL", "FAILED", "ABORTED"})
    void terminalCleanupRemovesInMemoryRunWithoutBusinessActions(String status) {
        var state = register(status);
        assertThat(service.cleanup(state.getRunId()).get("cleaned")).isEqualTo(true);
        assertThat(service.listRuns()).isEmpty();
        assertThatThrownBy(() -> service.snapshot(state.getRunId()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"snapshot", "pause", "resume", "abort", "cleanup", "stream"})
    void missingRunIsRejectedWithoutGatewayActions(String operation) {
        assertThatThrownBy(() -> {
            switch (operation) {
                case "snapshot" -> service.snapshot("missing");
                case "pause" -> service.pause("missing");
                case "resume" -> service.resume("missing");
                case "abort" -> service.abort("missing");
                case "cleanup" -> service.cleanup("missing");
                case "stream" -> service.stream("missing");
                default -> throw new AssertionError(operation);
            }
        }).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @SuppressWarnings("unchecked")
    private SimulationRunState register(String status) {
        var state = new SimulationRunState(mapper, mapper.createObjectNode());
        state.setStatus(status);
        ((Map<String, SimulationRunState>) ReflectionTestUtils.getField(service, "runs"))
                .put(state.getRunId(), state);
        return state;
    }
}
