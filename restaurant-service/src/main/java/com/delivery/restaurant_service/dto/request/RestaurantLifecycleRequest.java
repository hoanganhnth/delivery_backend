package com.delivery.restaurant_service.dto.request;

import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import jakarta.validation.constraints.NotNull;

public record RestaurantLifecycleRequest(
        @NotNull RestaurantStatus targetStatus,
        Long expectedVersion) {
}
