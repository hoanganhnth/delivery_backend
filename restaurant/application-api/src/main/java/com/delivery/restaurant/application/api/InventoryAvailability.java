package com.delivery.restaurant.application.api;

/** Advisory inventory availability signal used by checkout validation. */
public record InventoryAvailability(boolean hasEnoughStock, Integer availableQuantity) {
}
