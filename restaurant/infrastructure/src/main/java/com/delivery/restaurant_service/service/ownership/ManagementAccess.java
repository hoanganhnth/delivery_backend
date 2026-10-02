package com.delivery.restaurant_service.service.ownership;

/** Result of an authorized Restaurant management decision. */
public record ManagementAccess(boolean usedLegacyFallback) {
    public static ManagementAccess direct() {
        return new ManagementAccess(false);
    }

    public static ManagementAccess legacyFallback() {
        return new ManagementAccess(true);
    }
}
