package com.delivery.restaurant_service.dto.response;

import com.delivery.restaurant.application.api.InventoryReservationResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InventoryReservationResponse(
        UUID reservationId,
        Long orderId,
        Long restaurantId,
        String state,
        LocalDateTime expiresAt,
        List<Line> items) {

    public static InventoryReservationResponse from(InventoryReservationResult reservation) {
        return new InventoryReservationResponse(
                reservation.reservationId(),
                reservation.orderId(),
                reservation.restaurantId(),
                reservation.state(),
                reservation.expiresAt(),
                reservation.items().stream()
                        .map(line -> new Line(line.menuItemId(), line.quantity()))
                        .toList());
    }

    public record Line(Long menuItemId, Integer quantity) {
    }
}
