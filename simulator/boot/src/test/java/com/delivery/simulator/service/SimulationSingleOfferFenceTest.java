package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulationSingleOfferFenceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayClient gateway = mock(GatewayClient.class);
    private final SimulationService service = new SimulationService(mapper, new SimulatorProperties(), gateway);
    @AfterEach void shutdown() { service.shutdown(); }

    @ParameterizedTest
    @CsvSource({"AUTO_ACCEPT,true", "REJECT_AFTER_DELAY,true", "AUTO_ACCEPT,false", "REJECT_AFTER_DELAY,false"})
    void singleOfferChecksControlBeforeResponding(String behavior, boolean abortDuringRead) {
        var shipper = mapper.createObjectNode().put("id", "shipper-1").put("token", "fixture-token")
                .put("behavior", behavior).put("reactionDelaySeconds", 0);
        var scenario = mapper.createObjectNode();
        scenario.putArray("shippers").add(shipper);
        var state = new SimulationRunState(mapper, scenario);
        state.setStatus("RUNNING");
        state.setOrder(1L, "PENDING");
        when(gateway.get(eq("/api/deliveries/offers/current"), anyString(), anyString()))
                .thenAnswer(call -> {
                    if (abortDuringRead) state.abort();
                    return mapper.createObjectNode().put("orderId", 1L).put("deliveryId", 2L);
                });
        if (abortDuringRead) {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "inspectOffers", state))
                    .isInstanceOf(RuntimeException.class);
            assertThat(state.getAssignedShipperId()).isNull();
        } else {
            ReflectionTestUtils.invokeMethod(service, "inspectOffers", state);
            verify(gateway).post(eq("/api/deliveries/accept"), eq("fixture-token"),
                    argThat(body -> body.path("orderId").asLong() == 1L
                            && body.path("action").asText().equals(behavior.equals("AUTO_ACCEPT") ? "ACCEPT" : "REJECT")),
                    eq(state.getCorrelationId()));
            assertThat(state.getAssignedShipperId()).isEqualTo(behavior.equals("AUTO_ACCEPT") ? "shipper-1" : null);
        }
        verify(gateway).get(eq("/api/deliveries/offers/current-batch"), eq("fixture-token"), eq(state.getCorrelationId()));
        verify(gateway).get(eq("/api/deliveries/offers/current"), eq("fixture-token"), eq(state.getCorrelationId()));
        verifyNoMoreInteractions(gateway);
    }
}
