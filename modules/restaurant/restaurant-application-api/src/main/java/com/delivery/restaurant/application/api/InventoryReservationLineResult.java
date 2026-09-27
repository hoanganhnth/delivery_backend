package com.delivery.restaurant.application.api;

/** One menu-item quantity in an inventory reservation result. */
public record InventoryReservationLineResult(Long menuItemId, Integer quantity) {
}
