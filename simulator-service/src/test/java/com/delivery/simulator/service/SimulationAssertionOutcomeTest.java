package com.delivery.simulator.service;

import com.delivery.simulator.config.SimulatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SimulationAssertionOutcomeTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GatewayClient gateway = mock(GatewayClient.class);
    private final SimulationService runner = new SimulationService(mapper, new SimulatorProperties(), gateway);
    @AfterEach void shutdown() { runner.shutdown(); }

    @ParameterizedTest
    @CsvSource({
            "DELIVERED,DELIVERED,NONE,NONE,false,PASSED",
            "CANCELLED,REJECTED,NONE,NONE,false,PASSED",
            "DELIVERED,CANCELLED,NONE,NONE,false,FAILED",
            "SHIPPER_NOT_FOUND,SHIPPER_NOT_FOUND,NONE,NONE,false,PASSED",
            "DELIVERED,DELIVERED,shipper-1,shipper-2,false,FAILED",
            "DELIVERED,DELIVERED,shipper-1,shipper-1,false,PASSED",
            "DELIVERED,DELIVERED,NONE,NONE,true,PARTIAL",
            "CANCELLED,DELIVERED,NONE,NONE,true,PARTIAL"
    })
    @SuppressWarnings("unchecked")
    void assertionOutcomeDistinguishesMismatchAndMissingLedgerEvidence(String actual, String expected,
            String expectedShipper, String assigned, boolean ledger, String outcome) {
        var scenario = mapper.createObjectNode();
        var assertion = scenario.putArray("assertions").addObject().put("expectedTerminalState", expected);
        if (!expectedShipper.equals("NONE")) assertion.put("expectedShipperId", expectedShipper);
        if (ledger) assertion.put("expectedLedgerCount", 1);
        var state = new SimulationRunState(mapper, scenario);
        state.setOrder(1L, actual);
        if (!assigned.equals("NONE")) state.setAssignedShipperId(assigned);

        ReflectionTestUtils.invokeMethod(runner, "finishAssertions", state);

        assertThat(state.getStatus()).isEqualTo(outcome);
        var saved = ((List<Map<String, Object>>) state.snapshot().get("assertions")).get(0);
        assertThat(saved).containsEntry("id", "assertion-1")
                .containsEntry("status", ledger ? "SKIPPED" : outcome);
        assertThat(saved.get("actualValue")).isNotNull();
        verifyNoInteractions(gateway);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failureTakesPrecedenceOverSkippedObserverAssertion() {
        var scenario = mapper.createObjectNode();
        var assertions = scenario.putArray("assertions");
        assertions.addObject().put("id", "ledger").put("expectedTerminalState", "DELIVERED").put("expectedLedgerCount", 1);
        assertions.addObject().put("id", "terminal").put("expectedTerminalState", "DELIVERED");
        var state = new SimulationRunState(mapper, scenario);
        state.setOrder(1L, "CANCELLED");
        ReflectionTestUtils.invokeMethod(runner, "finishAssertions", state);
        assertThat(state.getStatus()).isEqualTo("FAILED");
        assertThat((List<Map<String, Object>>) state.snapshot().get("assertions"))
                .extracting(row -> row.get("status")).containsExactly("SKIPPED", "FAILED");
        verifyNoInteractions(gateway);
    }
}
