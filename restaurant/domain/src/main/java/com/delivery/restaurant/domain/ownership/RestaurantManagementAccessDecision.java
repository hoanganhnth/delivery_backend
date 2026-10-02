package com.delivery.restaurant.domain.ownership;

/** The result of an ownership decision; legacy access must remain observable during migration. */
public record RestaurantManagementAccessDecision(boolean usedLegacyFallback) {
    public static RestaurantManagementAccessDecision direct() {
        return new RestaurantManagementAccessDecision(false);
    }

    public static RestaurantManagementAccessDecision legacyFallback() {
        return new RestaurantManagementAccessDecision(true);
    }
}
