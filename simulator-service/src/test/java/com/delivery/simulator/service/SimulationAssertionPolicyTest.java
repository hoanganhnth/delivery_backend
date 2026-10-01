package com.delivery.simulator.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class SimulationAssertionPolicyTest {
    @ParameterizedTest
    @CsvSource({"false,false,PASSED", "false,true,PARTIAL", "true,false,FAILED", "true,true,FAILED"})
    void outcomePrecedenceIsIndependentOfObservationOrder(boolean failed, boolean skipped, String outcome) {
        assertThat(SimulationAssertionPolicy.runOutcome(failed, skipped)).isEqualTo(outcome);
    }

    @ParameterizedTest
    @CsvSource({"CANCELLED,REJECTED,PASSED", "DELIVERED,REJECTED,FAILED"})
    void rejectedAliasRequiresCanonicalCancelledState(String actual, String expected, String result) {
        assertThat(SimulationAssertionPolicy.evaluate(actual, null, expected, "", false).status()).isEqualTo(result);
    }
}
