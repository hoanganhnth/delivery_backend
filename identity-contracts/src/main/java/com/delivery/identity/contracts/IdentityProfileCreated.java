package com.delivery.identity.contracts;

import java.time.Instant;
import java.util.UUID;

public record IdentityProfileCreated(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        Long principalId,
        String profileType,
        Long profileId,
        long profileVersion) {

    public static final String TYPE = "identity.profile.created";
}
