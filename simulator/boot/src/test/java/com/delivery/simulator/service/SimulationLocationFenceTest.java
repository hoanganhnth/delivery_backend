package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulationLocationFenceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayClient gateway = mock(GatewayClient.class);
    private final SimulationService service = new SimulationService(mapper, new SimulatorProperties(), gateway);

    @AfterEach void shutdown() { service.shutdown(); }

    @Test
    void abortedRunCannotSendLocationAfterAnEarlierCallerControlCheck() {
        var state = new SimulationRunState(mapper, mapper.createObjectNode());
        state.abort();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "updateLocation", state,
                "shipper-1", "runtime-fixture-token", 10.0, 20.0, true))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void activeRunStillSendsLocationSnapshot() {
        var state = new SimulationRunState(mapper, mapper.createObjectNode());
        state.setStatus("RUNNING");
        ReflectionTestUtils.invokeMethod(service, "updateLocation", state,
                "shipper-1", "runtime-fixture-token", 10.0, 20.0, true);
        verify(gateway).post(eq("/api/tracking/shipper-locations/update"), eq("runtime-fixture-token"),
                argThat(body -> body.path("latitude").asDouble() == 10.0
                        && body.path("longitude").asDouble() == 20.0
                        && body.path("isOnline").asBoolean()), eq(state.getCorrelationId()));
        verifyNoMoreInteractions(gateway);
    }
}
