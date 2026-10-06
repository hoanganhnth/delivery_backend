package com.delivery.simulator.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SimulationAssertionPolicyTest {
    @ParameterizedTest
    @CsvSource({"false,false,PASSED", "false,true,PARTIAL", "true,false,FAILED", "true,true,FAILED"})
    void outcomePrecedenceIsIndependentOfObservationOrder(boolean failed, boolean skipped, String outcome) {
        assertThat(SimulationAssertionPolicy.runOutcome(failed, skipped)).isEqualTo(outcome);
    }

    @Test
    void everyTerminalActorAndObserverCombinationPreservesStatusAndExactMessage() {
        // Explicit acceptance table: the rejection alias is one-way and case-sensitive.
        Map<String, Set<String>> accepted = Map.of(
                "DELIVERED", Set.of("DELIVERED"),
                "CANCELLED", Set.of("CANCELLED"),
                "SHIPPER_NOT_FOUND", Set.of("SHIPPER_NOT_FOUND"),
                "REJECTED", Set.of("REJECTED", "CANCELLED"),
                "", Set.of(""));
        var terminals = Arrays.asList("DELIVERED", "CANCELLED", "SHIPPER_NOT_FOUND",
                "REJECTED", "delivered", "UNKNOWN", "", null);
        var expectedActors = Arrays.asList("", " ", "\t\n", "actor-1", "actor-2");
        var assignedActors = Arrays.asList(null, "", "actor-1", "actor-2", "ACTOR-1");
        for (var expected : accepted.keySet()) {
            for (var terminal : terminals) {
                for (var expectedActor : expectedActors) {
                    for (var assigned : assignedActors) {
                        for (boolean ledger : new boolean[]{false, true}) {
                            String status;
                            String message;
                            if (ledger) {
                                status = "SKIPPED";
                                message = "Ledger observer chưa được bật trong MVP runner";
                            } else if (terminal == null || !accepted.get(expected).contains(terminal)) {
                                status = "FAILED";
                                message = "Actual terminal=" + terminal + ", expected=" + expected;
                            } else if (!expectedActor.isBlank() && !expectedActor.equals(assigned)) {
                                status = "FAILED";
                                message = "Actual shipper=" + assigned + ", expected=" + expectedActor;
                            } else {
                                status = "PASSED";
                                message = "Verified through Gateway state polling: " + terminal;
                            }
                            var result = SimulationAssertionPolicy.evaluate(terminal, assigned, expected,
                                    expectedActor, ledger);
                            assertThat(result).as("terminal=%s expected=%s actor=%s assigned=%s ledger=%s",
                                    terminal, expected, expectedActor, assigned, ledger)
                                    .isEqualTo(new SimulationAssertionPolicy.Result(status, message));
                        }
                    }
                }
            }
        }
    }

    @Test
    void skipShortCircuitsNullExpectedValues() {
        assertThat(SimulationAssertionPolicy.evaluate(null, null, null, null, true))
                .isEqualTo(new SimulationAssertionPolicy.Result("SKIPPED",
                        "Ledger observer chưa được bật trong MVP runner"));
    }

    @Test
    void nullExpectedTerminalKeepsTheExistingExceptionType() {
        assertThatNullPointerException().isThrownBy(() ->
                SimulationAssertionPolicy.evaluate("DELIVERED", null, null, "", false));
    }

    @Test
    void terminalMismatchShortCircuitsNullExpectedShipper() {
        assertThat(SimulationAssertionPolicy.evaluate("CANCELLED", null, "DELIVERED", null, false))
                .isEqualTo(new SimulationAssertionPolicy.Result("FAILED",
                        "Actual terminal=CANCELLED, expected=DELIVERED"));
    }

    @Test
    void matchingTerminalWithNullExpectedShipperKeepsTheExistingExceptionType() {
        assertThatNullPointerException().isThrownBy(() ->
                SimulationAssertionPolicy.evaluate("DELIVERED", null, "DELIVERED", null, false));
    }
}
