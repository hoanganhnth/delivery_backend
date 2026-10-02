package com.delivery.restaurant_service.dto.response;

import com.delivery.restaurant.application.api.MenuItemInventoryResult;

public record MenuItemInventoryResponse(
        Long menuItemId,
        Integer onHandQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        Long revision) {

    public static MenuItemInventoryResponse from(MenuItemInventoryResult inventory) {
        return new MenuItemInventoryResponse(
                inventory.menuItemId(),
                inventory.onHandQuantity(),
                inventory.reservedQuantity(),
                inventory.availableQuantity(),
                inventory.revision());
    }
}
