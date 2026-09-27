package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.math.BigDecimal;

public record CreateMenuItemCommand(
        Long restaurantId,
        Long actorPrincipalId,
        Long legacyUserId,
        RestaurantActorRole actorRole,
        boolean principalOwnershipEnforced,
        String name,
        String description,
        BigDecimal price,
        String image) {}
