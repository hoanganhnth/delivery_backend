package com.delivery.simulator.domain;

/** Pure evaluation; the runner owns observation and journal/state updates. */
public final class SimulationAssertionPolicy {
    private SimulationAssertionPolicy() { }

    public record Result(String status, String actualValue) { }

    public static Result evaluate(String terminal, String assigned, String expectedTerminal,
                           String expectedShipper, boolean requiresLedgerObserver) {
        if (requiresLedgerObserver) {
            return new Result("SKIPPED", "Ledger observer chưa được bật trong MVP runner");
        }
        // Order represents restaurant rejection as CANCELLED; retain the UI alias.
        boolean terminalMatches = expectedTerminal.equals(terminal)
                || ("REJECTED".equals(expectedTerminal) && "CANCELLED".equals(terminal));
        if (!terminalMatches) {
            return new Result("FAILED", "Actual terminal=" + terminal + ", expected=" + expectedTerminal);
        }
        if (!expectedShipper.isBlank() && !expectedShipper.equals(assigned)) {
            return new Result("FAILED", "Actual shipper=" + assigned + ", expected=" + expectedShipper);
        }
        return new Result("PASSED", "Verified through Gateway state polling: " + terminal);
    }

    public static String runOutcome(boolean hasFailure, boolean hasSkipped) {
        if (hasFailure) return "FAILED";
        if (hasSkipped) return "PARTIAL";
        return "PASSED";
    }
}
