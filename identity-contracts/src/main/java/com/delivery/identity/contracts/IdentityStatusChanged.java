package com.delivery.identity.contracts;

import java.time.Instant;
import java.util.UUID;

public record IdentityStatusChanged(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        Long principalId,
        IdentityLifecycleStatus status,
        long lifecycleVersion,
        String reasonCode,
        Long changedByPrincipalId) {

    public static final String TYPE = "identity.status.changed";
}
