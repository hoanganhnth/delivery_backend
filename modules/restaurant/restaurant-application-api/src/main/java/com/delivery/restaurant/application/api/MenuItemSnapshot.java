package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MenuItemSnapshot(
        Long id,
        Long restaurantId,
        String name,
        String description,
        BigDecimal price,
        MenuItemStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String image,
        Long version) {}
