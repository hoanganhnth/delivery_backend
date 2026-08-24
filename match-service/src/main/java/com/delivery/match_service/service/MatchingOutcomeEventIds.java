package com.delivery.match_service.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Stable identities for Match business outcomes.  The command event is the
 * durable generation identity, so a retry or a scheduler replay must derive
 * the same result event rather than append another terminal outcome.
 */
public final class MatchingOutcomeEventIds {

    private MatchingOutcomeEventIds() {
    }

    public static UUID forCommandOutcome(String outcome, UUID commandEventId) {
        if (outcome == null || outcome.isBlank() || commandEventId == null) {
            throw new IllegalArgumentException("Match outcome and command eventId are required");
        }
        return UUID.nameUUIDFromBytes(
                ("match:" + outcome + ":" + commandEventId).getBytes(StandardCharsets.UTF_8));
    }
}
