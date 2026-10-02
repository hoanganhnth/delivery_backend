package com.delivery.restaurant.domain.inventory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Reservation identity and ledger state, independent of JPA and transport. */
public record InventoryReservation(UUID reservationId, Long orderId, Long userId,
        Long userPrincipalId, Long restaurantId, State state, LocalDateTime expiresAt,
        LocalDateTime createdAt, LocalDateTime updatedAt, List<Line> lines) {
    public enum State { RESERVED, COMMITTED, RELEASED, EXPIRED }
    public record Line(Long menuItemId, Integer quantity) { }
    public InventoryReservation { lines = List.copyOf(lines); }
    public InventoryReservation withState(State next) {
        return new InventoryReservation(reservationId, orderId, userId, userPrincipalId,
                restaurantId, next, expiresAt, createdAt, updatedAt, lines);
    }
}
