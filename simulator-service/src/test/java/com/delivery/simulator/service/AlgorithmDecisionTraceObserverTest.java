package com.delivery.simulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AlgorithmDecisionTraceObserverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimulationService simulationService = mock(SimulationService.class);
    private final AlgorithmDecisionTraceObserver observer =
            new AlgorithmDecisionTraceObserver(objectMapper, simulationService);

    @Test
    void forwardsVersionedTraceToTheReadOnlyRunObserver() throws Exception {
        observer.observe("""
                {
                  "eventId":"11111111-1111-1111-1111-111111111111",
                  "commandEventId":"22222222-2222-2222-2222-222222222222",
                  "eventVersion":1,
                  "orderId":101,
                  "deliveryId":202,
                  "algorithmId":"nearest-cod",
                  "algorithmVersion":"v1",
                  "decision":"SHIPPER_SELECTED",
                  "stages":[],
                  "candidates":[]
                }
                """);

        verify(simulationService).recordAlgorithmTrace(objectMapper.readTree("""
                {
                  "eventId":"11111111-1111-1111-1111-111111111111",
                  "commandEventId":"22222222-2222-2222-2222-222222222222",
                  "eventVersion":1,
                  "orderId":101,
                  "deliveryId":202,
                  "algorithmId":"nearest-cod",
                  "algorithmVersion":"v1",
                  "decision":"SHIPPER_SELECTED",
                  "stages":[],
                  "candidates":[]
                }
                """));
    }

    @Test
    void rejectsMalformedTraceSoKafkaCanRetryIt() {
        assertThatThrownBy(() -> observer.observe("{\"eventId\":\"missing-identities\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid matching decision trace");
    }
}
