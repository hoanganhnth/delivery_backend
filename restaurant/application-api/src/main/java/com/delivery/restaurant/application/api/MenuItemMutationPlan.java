package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import java.math.BigDecimal;

public record MenuItemMutationPlan(
        Long menuItemId,
        Long restaurantId,
        Long restaurantOwnerPrincipalId,
        String name,
        String description,
        BigDecimal price,
        MenuItemStatus status,
        String image) {}
