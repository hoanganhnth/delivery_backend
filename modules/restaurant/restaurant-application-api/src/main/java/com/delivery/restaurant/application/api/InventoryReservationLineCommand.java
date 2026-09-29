package com.delivery.restaurant.application.api;

/** One menu-item quantity in an inventory reservation command. */
public record InventoryReservationLineCommand(Long menuItemId, Integer quantity) {
}
