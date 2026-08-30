package com.delivery.identity.contracts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

/**
 * Immutable execution boundary copied with a business aggregate and every
 * downstream event. A missing context is intentionally interpreted as REAL so
 * additive schema rollouts do not turn historical events into simulations.
 */
public record SimulationContext(
        ExecutionMode mode,
        UUID runId,
        UUID cohortId,
        Long bindingVersion) {

    public enum ExecutionMode { REAL, SIMULATION }

    public static SimulationContext real() {
        return new SimulationContext(ExecutionMode.REAL, null, null, null);
    }

    public static SimulationContext orReal(SimulationContext context) {
        return context == null ? real() : context;
    }

    @JsonIgnore
    public boolean isSimulation() {
        return mode == ExecutionMode.SIMULATION;
    }

    /** Reject mixed or incomplete context instead of silently crossing cohorts. */
    public void requireValid() {
        if (mode == null) {
            throw new IllegalArgumentException("Simulation execution mode is required");
        }
        if (mode == ExecutionMode.REAL) {
            if (runId != null || cohortId != null || bindingVersion != null) {
                throw new IllegalArgumentException("REAL context must not carry simulation identity");
            }
            return;
        }
        if (runId == null || cohortId == null || bindingVersion == null || bindingVersion <= 0) {
            throw new IllegalArgumentException(
                    "SIMULATION context requires runId, cohortId and positive bindingVersion");
        }
    }
}
