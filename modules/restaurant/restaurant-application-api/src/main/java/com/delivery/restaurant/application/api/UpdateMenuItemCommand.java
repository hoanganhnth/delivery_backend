package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.math.BigDecimal;

public record UpdateMenuItemCommand(
        Long menuItemId,
        Long actorPrincipalId,
        Long legacyUserId,
        RestaurantActorRole actorRole,
        boolean principalOwnershipEnforced,
        String name,
        String description,
        BigDecimal price,
        MenuItemStatus status,
        String image,
        Long requestedRestaurantId) {}
