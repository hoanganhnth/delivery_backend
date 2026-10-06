package com.delivery.search.domain;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/** Classifies a scripted noop. Atomic claim and legacy CAS remain storage operations. */
public final class CheckpointReplayPolicy {
    private CheckpointReplayPolicy() { }
    public enum Decision { EXACT_REPLAY, STALE, UPGRADE_LEGACY }
    public record Expected(String eventId, String occurredAt, String action) { }
    public record Stored(String eventId, String occurredAt, String action, String fingerprint) { }

    public static Decision classify(Expected expected, Stored stored, String fingerprint, String checkpointId) {
        String eventId = stored.eventId();
        String occurredAt = stored.occurredAt();
        String action = stored.action();
        String storedFingerprint = stored.fingerprint();
        String expectedOccurredAt = expected.occurredAt();
        String expectedAction = expected.action();

        if (expected.eventId().equals(eventId)) {
            if (!sameOccurredAt(expectedOccurredAt, occurredAt) || !expectedAction.equals(action)) {
                throw new IllegalArgumentException(
                        "entity-sync eventId replay has contradictory metadata for " + checkpointId);
            }
            if (storedFingerprint == null) {
                return Decision.UPGRADE_LEGACY;
            }
            if (!fingerprint.equals(storedFingerprint)) {
                throw new IllegalArgumentException(
                        "entity-sync eventId replay has contradictory payload for " + checkpointId);
            }
            return Decision.EXACT_REPLAY;
        }
        if (occurredAt == null) {
            throw new IllegalStateException("Checkpoint has no comparable occurredAt for " + checkpointId);
        }
        int ordering = compareOccurredAt(occurredAt, expectedOccurredAt);
        if (ordering > 0) {
            return Decision.STALE;
        }
        if (ordering == 0) {
            throw new IllegalArgumentException(
                    "Conflicting entity-sync events share the same occurredAt for " + checkpointId);
        }
        throw new IllegalStateException("Checkpoint claim regressed for " + checkpointId);
    }

    private static boolean sameOccurredAt(String expected, String stored) {
        return parseOccurredAt(expected).equals(parseOccurredAt(stored));
    }

    private static int compareOccurredAt(String left, String right) {
        return parseOccurredAt(left).compareTo(parseOccurredAt(right));
    }

    private static LocalDateTime parseOccurredAt(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException localFormat) {
            try {
                // Spring Data Elasticsearch mappings written before this
                // checkpoint store can expose a Date field with an explicit
                // offset (typically a trailing Z). Their producer used a
                // LocalDateTime, so normalize the textual representation to
                // the original local fields before comparing it to a retry.
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (RuntimeException offsetFormat) {
                offsetFormat.addSuppressed(localFormat);
                throw new IllegalStateException("Checkpoint has an invalid occurredAt value", offsetFormat);
            }
        }
    }

}
