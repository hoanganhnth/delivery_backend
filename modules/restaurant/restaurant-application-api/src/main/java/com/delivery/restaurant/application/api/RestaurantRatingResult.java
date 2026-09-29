package com.delivery.restaurant.application.api;

import java.time.LocalDateTime;

/** Framework-free rating result returned by the application boundary. */
public record RestaurantRatingResult(
        Long id,
        Long restaurantId,
        Long customerId,
        Long orderId,
        Integer rating,
        String comment,
        String status,
        LocalDateTime createdAt) {
}
