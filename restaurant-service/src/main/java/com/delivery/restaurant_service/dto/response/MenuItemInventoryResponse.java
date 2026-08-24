package com.delivery.restaurant_service.dto.response;

import com.delivery.restaurant_service.entity.MenuItemInventory;

public record MenuItemInventoryResponse(
        Long menuItemId,
        Integer onHandQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        Long revision) {

    public static MenuItemInventoryResponse from(MenuItemInventory inventory) {
        return new MenuItemInventoryResponse(
                inventory.getMenuItemId(),
                inventory.getOnHandQuantity(),
                inventory.getReservedQuantity(),
                inventory.availableQuantity(),
                inventory.getRevision());
    }
}
