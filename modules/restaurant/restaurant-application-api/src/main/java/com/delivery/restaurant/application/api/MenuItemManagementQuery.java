package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantActorRole;

public record MenuItemManagementQuery(
        Long restaurantId,
        Long principalId,
        Long legacyUserId,
        RestaurantActorRole actorRole,
        boolean principalOwnershipEnforced,
        int page,
        int size) {}
