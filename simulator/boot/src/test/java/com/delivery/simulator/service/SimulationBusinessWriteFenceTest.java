package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimulationBusinessWriteFenceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayClient gateway = mock(GatewayClient.class);
    private final SimulationService service = new SimulationService(mapper, new SimulatorProperties(), gateway);

    @AfterEach void shutdown() { service.shutdown(); }

    @ParameterizedTest
    @ValueSource(strings = {"createOrder", "confirmRestaurant", "transitionDelivery"})
    void abortedRunCannotSendBusinessWrite(String operation) {
        var state = state();
        state.abort();
        assertThatThrownBy(() -> {
            if (operation.equals("transitionDelivery")) {
                ReflectionTestUtils.invokeMethod(service, operation, state, "fixture-token", "DELIVERED");
            } else {
                ReflectionTestUtils.invokeMethod(service, operation, state, "fixture-token");
            }
        }).isInstanceOf(RuntimeException.class);
        assertThat(state.getDeliveryStatus()).isEqualTo("ASSIGNED");
        verifyNoInteractions(gateway);
    }

    @Test
    void abortWhileFetchingQuotePreventsOrderCreation() {
        var state = state();
        when(gateway.post(eq("/api/orders/checkout-preview"), anyString(), any(), anyString()))
                .thenAnswer(call -> {
                    state.abort();
                    return mapper.createObjectNode().put("quoteId", "quote-1");
                });
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "createOrder", state, "fixture-token"))
                .isInstanceOf(RuntimeException.class);
        verify(gateway).post(eq("/api/orders/checkout-preview"), anyString(), any(), anyString());
        verifyNoMoreInteractions(gateway);
        assertThat(state.getOrderId()).isEqualTo(1L);
    }

    @Test
    void activeDeliveryTransitionPreservesRequestAndStateUpdates() {
        var state = state();
        ReflectionTestUtils.invokeMethod(service, "transitionDelivery", state, "fixture-token", "DELIVERED");
        verify(gateway).put(eq("/api/deliveries/2/status?status=DELIVERED"), eq("fixture-token"),
                isNull(), eq(state.getCorrelationId()));
        assertThat(state.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(state.getOrderStatus()).isEqualTo("DELIVERED");
        verifyNoMoreInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"quote-1", "", "MISSING"})
    void activeCheckoutUsesQuoteAndIdempotencyHeaderOnlyWhenQuoteExists(String quoteId) {
        var state = state();
        var quote = mapper.createObjectNode();
        if (!quoteId.equals("MISSING")) quote.put("quoteId", quoteId);
        boolean quoted = quoteId.equals("quote-1");
        when(gateway.post(eq("/api/orders/checkout-preview"), anyString(), any(), anyString())).thenReturn(quote);
        when(gateway.postWithHeaders(eq("/api/orders"), anyString(), any(), anyString(), any()))
                .thenReturn(mapper.createObjectNode().put("id", 91L).put("status", "PENDING"));

        ReflectionTestUtils.invokeMethod(service, "createOrder", state, "fixture-token");

        verify(gateway).post(eq("/api/orders/checkout-preview"), eq("fixture-token"), any(), eq(state.getCorrelationId()));
        verify(gateway).postWithHeaders(eq("/api/orders"), eq("fixture-token"),
                argThat(body -> body.path("paymentMethod").asText().equals("COD")
                        && body.path("items").get(0).path("quantity").asInt() == 1
                        && (quoted ? body.path("quoteId").asText().equals(quoteId) : !body.has("quoteId"))),
                eq(state.getCorrelationId()), argThat(headers -> quoted
                        ? headers.containsKey("Idempotency-Key")
                            && java.util.UUID.fromString(headers.get("Idempotency-Key")) != null
                        : headers.isEmpty()));
        assertThat(state.getOrderId()).isEqualTo(91L);
        assertThat(state.getOrderStatus()).isEqualTo("PENDING");
        verifyNoMoreInteractions(gateway);
    }

    private SimulationRunState state() {
        var state = new SimulationRunState(mapper, mapper.createObjectNode());
        state.setStatus("RUNNING");
        state.setOrder(1L, "PENDING");
        state.setDelivery(2L, "ASSIGNED");
        return state;
    }
}
