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

class SimulationTriggerFenceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayClient gateway = mock(GatewayClient.class);
    private final SimulationService service = new SimulationService(mapper, new SimulatorProperties(), gateway);
    @AfterEach void shutdown() { service.shutdown(); }

    @ParameterizedTest
    @CsvSource({"CUSTOMER_CANCEL,true", "RESTAURANT_REJECT,true", "SHIPPER_DISCONNECT,true", "CANCEL_ASSIGNMENT,true",
            "CUSTOMER_CANCEL,false", "RESTAURANT_REJECT,false", "SHIPPER_DISCONNECT,false", "CANCEL_ASSIGNMENT,false"})
    void zeroDelayActionsRespectAbortAndActiveActionsFireOnce(String type, boolean aborted) {
        var scenario = mapper.createObjectNode();
        scenario.putArray("shippers").addObject().put("id", "shipper-1").put("token", "shipper-token")
                .put("behavior", "CANCEL_AFTER_ACCEPT").put("reactionDelaySeconds", 0);
        scenario.putObject("restaurant").put("id", 7L);
        scenario.putArray("triggers").addObject().put("enabled", true).put("type", type)
                .put("atStage", "ASSIGNED").put("delaySecondsAfterStage", 0);
        var state = new SimulationRunState(mapper, scenario);
        state.setStatus("RUNNING");
        state.setOrder(1L, "PENDING");
        state.setAssignedShipperId("shipper-1");
        if (aborted) {
            state.abort();
            assertThatThrownBy(() -> invoke(state, type)).isInstanceOf(RuntimeException.class);
            verifyNoInteractions(gateway);
        } else {
            invoke(state, type);
            invoke(state, type);
            switch (type) {
                case "CUSTOMER_CANCEL" -> verify(gateway).put(eq("/api/orders/1/cancel"), eq("customer-token"),
                        any(), eq(state.getCorrelationId()));
                case "RESTAURANT_REJECT" -> verify(gateway).post(eq("/api/restaurants/orders/1/reject"), eq("owner-token"),
                        argThat(body -> body.path("restaurantId").asLong() == 7L), eq(state.getCorrelationId()));
                case "SHIPPER_DISCONNECT" -> verify(gateway).post(eq("/api/tracking/shipper-locations/offline"),
                        eq("shipper-token"), isNull(), eq(state.getCorrelationId()));
                case "CANCEL_ASSIGNMENT" -> {
                    verify(gateway).post(eq("/api/deliveries/cancel-assignment"), eq("shipper-token"),
                            argThat(body -> body.path("orderId").asLong() == 1L), eq(state.getCorrelationId()));
                    assertThat(state.getAssignedShipperId()).isNull();
                }
                default -> throw new AssertionError(type);
            }
            verifyNoMoreInteractions(gateway);
        }
    }

    private void invoke(SimulationRunState state, String type) {
        if (type.equals("CANCEL_ASSIGNMENT")) {
            ReflectionTestUtils.invokeMethod(service, "handleAssigned", state, "customer-token", "owner-token");
        } else {
            ReflectionTestUtils.invokeMethod(service, "fireTriggers", state, "ASSIGNED", "customer-token", "owner-token");
        }
    }
}
