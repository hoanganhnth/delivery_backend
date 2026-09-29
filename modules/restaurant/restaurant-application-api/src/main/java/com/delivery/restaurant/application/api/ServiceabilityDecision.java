package com.delivery.restaurant.application.api;

/** Internal serviceability decision returned to checkout callers. */
public record ServiceabilityDecision(
        boolean enabled,
        boolean serviceable,
        Long zoneId,
        Long zoneRevision,
        String reason) {
}
