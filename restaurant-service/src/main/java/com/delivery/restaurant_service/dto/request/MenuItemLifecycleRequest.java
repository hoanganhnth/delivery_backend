package com.delivery.restaurant_service.dto.request;

import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import jakarta.validation.constraints.NotNull;

public record MenuItemLifecycleRequest(
        @NotNull MenuItemStatus targetStatus,
        Long expectedVersion) {
}
