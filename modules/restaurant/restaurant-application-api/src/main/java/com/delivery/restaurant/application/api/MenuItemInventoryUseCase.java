package com.delivery.restaurant.application.api;

import java.util.UUID;

/** Port for transactional menu-item inventory and reservation operations. */
public interface MenuItemInventoryUseCase {

    InventoryReservationResult reserve(InventoryReservationCommand command);

    InventoryReservationResult commit(UUID reservationId, Long orderId);

    InventoryReservationResult release(UUID reservationId, Long orderId);

    int expireReservations();

    MenuItemInventoryResult getInventory(Long menuItemId);

    InventoryAvailability availability(Long restaurantId, Long menuItemId, Integer quantity);

    MenuItemInventoryResult updateInventory(Long menuItemId, UpdateMenuItemInventoryCommand command);
}
