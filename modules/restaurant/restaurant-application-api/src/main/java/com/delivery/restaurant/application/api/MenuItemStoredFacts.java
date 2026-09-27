package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MenuItemStoredFacts(
        Long menuItemId,
        Long restaurantId,
        Long restaurantOwnerPrincipalId,
        Long restaurantCreatorId,
        String name,
        String description,
        BigDecimal price,
        MenuItemStatus status,
        String image,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version) {}
