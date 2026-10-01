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

class SimulationBatchOfferFenceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayClient gateway = mock(GatewayClient.class);
    private final SimulationService service = new SimulationService(mapper, new SimulatorProperties(), gateway);

    @AfterEach void shutdown() { service.shutdown(); }

    @ParameterizedTest
    @CsvSource({"AUTO_ACCEPT,true", "REJECT_AFTER_DELAY,true", "AUTO_ACCEPT,false", "REJECT_AFTER_DELAY,false"})
    void batchActionChecksControlAfterOfferRead(String behavior, boolean abortDuringRead) throws Exception {
        var shipper = mapper.createObjectNode().put("id", "shipper-1").put("behavior", behavior)
                .put("reactionDelaySeconds", 0).put("initialLat", 10).put("initialLng", 20);
        var scenario = mapper.createObjectNode();
        scenario.putArray("shippers").add(shipper);
        var state = new SimulationRunState(mapper, scenario);
        state.setStatus("RUNNING");
        state.setOrder(1L, "PENDING");
        var response = mapper.readTree("{\"batchId\":\"batch-1\",\"offers\":[{\"orderId\":1}]}");
        when(gateway.get(eq("/api/deliveries/offers/current-batch"), anyString(), anyString()))
                .thenAnswer(call -> {
                    if (abortDuringRead) state.abort();
                    return response;
                });

        if (abortDuringRead) {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "inspectBatchOffer", state,
                    shipper, "shipper-1", "fixture-token")).isInstanceOf(RuntimeException.class);
            assertThat(state.getAssignedShipperId()).isNull();
        } else {
            Boolean handled = ReflectionTestUtils.invokeMethod(service, "inspectBatchOffer", state,
                    shipper, "shipper-1", "fixture-token");
            assertThat(handled).isTrue();
            String action = behavior.equals("AUTO_ACCEPT") ? "accept" : "reject";
            verify(gateway).post(eq("/api/deliveries/batch/" + action), eq("fixture-token"),
                    argThat(body -> body.path("batchId").asText().equals("batch-1")), eq(state.getCorrelationId()));
            assertThat(state.getAssignedShipperId()).isEqualTo(behavior.equals("AUTO_ACCEPT") ? "shipper-1" : null);
        }
        verify(gateway).get(eq("/api/deliveries/offers/current-batch"), eq("fixture-token"), eq(state.getCorrelationId()));
        verifyNoMoreInteractions(gateway);
    }
}
