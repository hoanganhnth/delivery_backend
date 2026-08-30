package com.delivery.simulator.service;

import com.delivery.identity.contracts.SimulationContext;
import java.util.UUID;

/** Auth-owned simulation actor binding boundary. Access tokens stay memory-only. */
public interface SimulationActorPoolClient {
    BoundActor bind(Long principalId, UUID runId, UUID cohortId);

    void unbind(Long principalId, UUID runId, long bindingVersion);

    record BoundActor(Long principalId, SimulationContext context, String accessToken) {
        public BoundActor {
            if (principalId == null || principalId <= 0 || context == null
                    || accessToken == null || accessToken.isBlank()) {
                throw new IllegalArgumentException("bound simulation actor is incomplete");
            }
            context.requireValid();
        }
    }
}
