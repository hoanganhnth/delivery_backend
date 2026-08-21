package com.delivery.identity.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Service-owned mapping from Auth's stable principal to the Shipper aggregate.
 * Resource services consume this projection instead of treating a user-profile
 * ID as a shipper domain ID or calling shipper-service on their request path.
 */
public record ShipperIdentityUpserted(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        Long principalId,
        Long legacyUserId,
        Long shipperId,
        long mappingVersion) {

    public static final String TYPE = "shipper.identity.upserted";
}
