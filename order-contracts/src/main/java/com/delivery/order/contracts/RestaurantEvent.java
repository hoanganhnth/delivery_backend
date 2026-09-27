package com.delivery.order.contracts;

import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable restaurant lifecycle event consumed by order. */
public record RestaurantEvent(
        UUID eventId,
        Long restaurantId,
        Long actorUserId,
        Long orderId,
        String status,
        String action,
        Integer estimatedPrepTime,
        String rejectionReason,
        LocalDateTime processedAt,
        String notes) {
}
