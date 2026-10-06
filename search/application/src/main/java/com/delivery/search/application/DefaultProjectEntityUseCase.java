package com.delivery.search.application;

import com.delivery.search.application.api.ProjectEntityUseCase;
import com.delivery.search.application.api.ProjectionInput;
import com.delivery.search.application.api.ProjectionPorts;
import com.delivery.search.domain.EntitySyncRules;

/** Admission and replay orchestration; transport, storage and telemetry live behind ports. */
public final class DefaultProjectEntityUseCase implements ProjectEntityUseCase {
    private final ProjectionPorts ports;
    public DefaultProjectEntityUseCase(ProjectionPorts ports) { this.ports = ports; }

    @Override
    public void project(ProjectionInput input) {
        EntitySyncRules.validate(input == null ? null : input.metadata());
        ProjectionPorts.Claim claim = ports.claim(input, ports.fingerprint(input));
        if (claim == ProjectionPorts.Claim.STALE) {
            ports.stale(input);
            return;
        }
        ports.received(input);
        // Preserve the host's catch boundary: admission/fingerprint/claim failures propagate unchanged.
        try {
            ports.write(input);
            if ("DELETE".equalsIgnoreCase(input.action())) {
                ports.tombstoneApplied();
            }
        } catch (Exception failure) {
            ports.replayFailed(input, failure);
            throw new IllegalStateException("Failed to synchronize search entity", failure);
        }
    }
}
