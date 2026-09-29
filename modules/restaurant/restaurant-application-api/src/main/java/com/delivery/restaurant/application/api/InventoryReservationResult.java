package com.delivery.restaurant.application.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Framework-free inventory reservation result returned to transport adapters. */
public record InventoryReservationResult(
        UUID reservationId,
        Long orderId,
        Long restaurantId,
        String state,
        LocalDateTime expiresAt,
        List<InventoryReservationLineResult> items) {
}
