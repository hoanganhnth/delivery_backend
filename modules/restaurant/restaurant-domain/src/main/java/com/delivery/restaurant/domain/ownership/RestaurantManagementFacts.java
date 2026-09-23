package com.delivery.restaurant.domain.ownership;

/** Ownership facts read from the Restaurant aggregate for a management decision. */
public record RestaurantManagementFacts(Long ownerPrincipalId, Long creatorId) {
    public RestaurantManagementFacts {
        if (ownerPrincipalId != null && ownerPrincipalId <= 0) {
            throw new IllegalArgumentException("ownerPrincipalId must be positive");
        }
    }
}
