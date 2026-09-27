package com.delivery.restaurant.application.api;

/** Framework-free inventory ledger projection. */
public record MenuItemInventoryResult(
        Long menuItemId,
        Integer onHandQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        Long revision) {
}
