package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulationPollFailureTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayClient gateway = mock(GatewayClient.class);
    private final SimulationService runner = new SimulationService(mapper, new SimulatorProperties(), gateway);
    @AfterEach void shutdown() { runner.shutdown(); }

    @ParameterizedTest
    @CsvSource({"false,429,true", "false,404,false", "false,500,false",
            "true,429,true", "true,404,true", "true,500,false"})
    void pollDistinguishesRetryableMissingDeliveryAndFatalFailure(boolean delivery, int status, boolean tolerated) {
        var state = state();
        var failure = new GatewayClient.GatewayException(status, "fixture", "fixture failure");
        if (delivery) when(gateway.get(eq("/api/orders/1"), anyString(), anyString()))
                .thenReturn(mapper.createObjectNode().put("status", "CONFIRMED"));
        String path = delivery ? "/api/deliveries/order/1" : "/api/orders/1";
        when(gateway.get(eq(path), anyString(), anyString())).thenThrow(failure);
        if (tolerated) {
            poll(state);
        } else {
            assertThatThrownBy(() -> poll(state)).isSameAs(failure);
        }
        assertThat(state.getOrderStatus()).isEqualTo(delivery ? "CONFIRMED" : "PENDING");
        assertThat(state.getDeliveryId()).isNull();
        verify(gateway).get(eq("/api/orders/1"), eq("fixture-token"), eq(state.getCorrelationId()));
        if (delivery) verify(gateway).get(eq("/api/deliveries/order/1"), eq("fixture-token"), eq(state.getCorrelationId()));
        verifyNoMoreInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "42"})
    void nonObjectDeliveryDoesNotReplaceKnownState(String payload) throws Exception {
        var state = state();
        state.setDelivery(2L, "ASSIGNED");
        when(gateway.get(eq("/api/orders/1"), anyString(), anyString()))
                .thenReturn(mapper.createObjectNode().put("status", "CONFIRMED"));
        when(gateway.get(eq("/api/deliveries/order/1"), anyString(), anyString())).thenReturn(mapper.readTree(payload));
        poll(state);
        assertThat(state.getDeliveryId()).isEqualTo(2L);
        assertThat(state.getDeliveryStatus()).isEqualTo("ASSIGNED");
    }

    @Test
    void legacyDeliveryIdentifierAndUserIdAreMappedToConfiguredActor() {
        var scenario = mapper.createObjectNode();
        scenario.putArray("shippers").addObject().put("id", "virtual-shipper").put("userId", 99L);
        var state = new SimulationRunState(mapper, scenario);
        state.setOrder(1L, "PENDING");
        when(gateway.get(eq("/api/orders/1"), anyString(), anyString())).thenReturn(mapper.createObjectNode());
        when(gateway.get(eq("/api/deliveries/order/1"), anyString(), anyString()))
                .thenReturn(mapper.createObjectNode().put("deliveryId", 2L).put("status", "ASSIGNED")
                        .put("shipperId", "99").put("offeredShipperId", "virtual-shipper"));
        poll(state);
        assertThat(state.getDeliveryId()).isEqualTo(2L);
        assertThat(state.getAssignedShipperId()).isEqualTo("virtual-shipper");
        assertThat(state.getOrderStatus()).isEqualTo("PENDING");
    }

    @Test
    void runWithoutOrderNeverPollsGateway() {
        poll(new SimulationRunState(mapper, mapper.createObjectNode()));
        verifyNoInteractions(gateway);
    }

    private void poll(SimulationRunState state) {
        ReflectionTestUtils.invokeMethod(runner, "pollState", state, "fixture-token");
    }

    private SimulationRunState state() {
        var state = new SimulationRunState(mapper, mapper.createObjectNode());
        state.setOrder(1L, "PENDING");
        return state;
    }
}
