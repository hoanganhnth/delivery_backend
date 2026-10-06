package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SimulationTransientPollRecoveryTest {
    @Test
    @SuppressWarnings("unchecked")
    void triggerInjectsOnePollFailureAndRunnerRecoversWithoutMutationOrRearming() throws Exception {
        var mapper = new ObjectMapper();
        var properties = new SimulatorProperties();
        properties.setGatewayBaseUrl("http://127.0.0.1:8080");
        var faults = new GatewayFaultInjection();
        var gateway = new GatewayClient(mapper, properties, faults);
        var transport = mock(HttpClient.class);
        ReflectionTestUtils.setField(gateway, "httpClient", transport);
        List<HttpRequest> sent = new ArrayList<>();
        when(transport.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            sent.add(request);
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(request.uri().getPath().startsWith("/api/orders/")
                    ? "{\"id\":1,\"status\":\"CONFIRMED\"}"
                    : "{\"id\":2,\"status\":\"ASSIGNED\"}");
            return response;
        });
        var runner = new SimulationService(mapper, properties, gateway, null, null, null, null, faults);
        try {
            var scenario = mapper.createObjectNode();
            scenario.putArray("triggers").addObject().put("enabled", true).put("type", "NETWORK_DELAY")
                    .put("atStage", "CONFIRMED").put("delaySecondsAfterStage", 0);
            var state = new SimulationRunState(mapper, scenario);
            state.setStatus("RUNNING");
            state.setOrder(1L, "PENDING");
            ReflectionTestUtils.invokeMethod(runner, "fireTriggers", state, "CONFIRMED", "fixture-token", "owner-token");

            ReflectionTestUtils.invokeMethod(runner, "pollState", state, "fixture-token");
            assertThat(sent).isEmpty();
            assertThat(state.getOrderStatus()).isEqualTo("PENDING");
            assertThat(state.getDeliveryId()).isNull();

            ReflectionTestUtils.invokeMethod(runner, "pollState", state, "fixture-token");
            assertThat(state.getOrderStatus()).isEqualTo("CONFIRMED");
            assertThat(state.getDeliveryStatus()).isEqualTo("ASSIGNED");
            assertThat(state.getDeliveryId()).isEqualTo(2L);
            ReflectionTestUtils.invokeMethod(runner, "fireTriggers", state, "CONFIRMED", "fixture-token", "owner-token");
            ReflectionTestUtils.invokeMethod(runner, "pollState", state, "fixture-token");
            assertThat(sent).hasSize(4).allSatisfy(request -> {
                assertThat(request.method()).isEqualTo("GET");
                assertThat(request.headers().firstValue("X-Correlation-Id")).contains(state.getCorrelationId());
                assertThat(request.headers().firstValue("Authorization")).contains("Bearer fixture-token");
            });
        } finally {
            runner.shutdown();
        }
    }
}
