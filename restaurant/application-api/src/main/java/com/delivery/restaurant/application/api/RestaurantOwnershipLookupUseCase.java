package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantActorRole;

public interface RestaurantOwnershipLookupUseCase {
    RestaurantOwnershipLookupResult internalCheck(Long restaurantId, Long ownerId, Long legacyOwnerId, boolean enforced);
    boolean canDecideOrder(Long restaurantId, RestaurantActorRole actorRole, Long principalId, Long legacyUserId, boolean enforced);
}
