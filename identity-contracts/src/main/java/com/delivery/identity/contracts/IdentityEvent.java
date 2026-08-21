package com.delivery.identity.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Base metadata carried by every identity event. Event identity, not Kafka
 * offset, is the idempotency key for consumers.
 */
public record IdentityEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId) {

    public static final int SCHEMA_VERSION = 1;
}
