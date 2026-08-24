package com.delivery.restaurant_service.service;

public record ServiceabilityDecision(
        boolean enabled,
        boolean serviceable,
        Long zoneId,
        Long zoneRevision,
        String reason) {

    public static ServiceabilityDecision disabled() {
        return new ServiceabilityDecision(false, false, null, null, "CAPABILITY_DISABLED");
    }

    public static ServiceabilityDecision unavailable(String reason) {
        return new ServiceabilityDecision(true, false, null, null, reason);
    }
}
