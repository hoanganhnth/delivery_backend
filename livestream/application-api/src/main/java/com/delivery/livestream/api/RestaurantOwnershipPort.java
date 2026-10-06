package com.delivery.livestream.api;

/** The host performs the internal HTTP lookup and retains fail-closed behavior. */
@FunctionalInterface
public interface RestaurantOwnershipPort {
    void requireOwnedBy(Long restaurantId, Long principalId, Long legacyUserId);
}
