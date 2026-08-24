package com.delivery.restaurant_service.dto.response;

import com.delivery.restaurant_service.entity.MenuItemInventoryReservation;

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

    public static InventoryReservationResponse from(MenuItemInventoryReservation reservation) {
        return new InventoryReservationResponse(
                reservation.getReservationId(),
                reservation.getOrderId(),
                reservation.getRestaurantId(),
                reservation.getState().name(),
                reservation.getExpiresAt(),
                reservation.getLines().stream()
                        .map(line -> new Line(line.getMenuItemId(), line.getQuantity()))
                        .toList());
    }

    public record Line(Long menuItemId, Integer quantity) {
    }
}
