package com.delivery.restaurant.application.api;

import java.util.List;
import java.util.UUID;

/** Framework-free input for reserving menu-item inventory. */
public record InventoryReservationCommand(
        UUID reservationId,
        Long orderId,
        Long userId,
        Long userPrincipalId,
        Long restaurantId,
        List<InventoryReservationLineCommand> items) {
}
