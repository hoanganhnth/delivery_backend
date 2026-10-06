package com.delivery.search.domain;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Existing Search envelope admission and projection ordering; no transport dependencies. */
public final class EntitySyncRules {
    private EntitySyncRules() { }

    public record Metadata(UUID eventId, LocalDateTime occurredAt, String entityType,
                           String action, String entityId, boolean payloadPresent,
                           Long aggregateVersion, LocalDateTime deletedAt) { }

    /** Checks in the original order so the first error and its message remain stable. */
    public static void validate(Metadata event) {
        if (event == null || event.eventId() == null || event.occurredAt() == null
                || event.entityType() == null || event.entityType().isBlank()
                || event.action() == null || event.action().isBlank()
                || event.entityId() == null || event.entityId().isBlank()) {
            throw new IllegalArgumentException(
                    "stable eventId, occurredAt, entityType, action and entityId are required");
        }
        if (!java.util.Set.of("CREATE", "UPDATE", "DELETE")
                .contains(event.action().toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported entity action: " + event.action());
        }
        String entityType = event.entityType().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("RESTAURANT", "DISH").contains(entityType)) {
            throw new IllegalArgumentException("Unsupported entity type: " + event.entityType());
        }
        if (!"DELETE".equalsIgnoreCase(event.action()) && !event.payloadPresent()) {
            throw new IllegalArgumentException("payload is required for create/update");
        }
        if (event.aggregateVersion() != null && event.aggregateVersion() < 1) {
            throw new IllegalArgumentException("aggregateVersion must be positive");
        }
        if ("DELETE".equalsIgnoreCase(event.action()) && event.deletedAt() != null
                && event.deletedAt().isAfter(event.occurredAt())) {
            throw new IllegalArgumentException("deletedAt cannot be after occurredAt");
        }
    }

    /** Aggregate versions take precedence over the legacy UTC nanosecond clock. */
    public static long projectionVersion(LocalDateTime occurredAt, Long aggregateVersion) {
        if (aggregateVersion != null) {
            return aggregateVersion;
        }
        try {
            return Math.addExact(
                    Math.multiplyExact(occurredAt.toEpochSecond(ZoneOffset.UTC), 1_000_000_000L),
                    occurredAt.getNano());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("entity-sync occurredAt cannot be represented as a version", exception);
        }
    }
}
