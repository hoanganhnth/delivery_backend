package com.delivery.restaurant.infrastructure.inventory;

public class InventoryResourceNotFoundException extends RuntimeException {

    public InventoryResourceNotFoundException(String message) {
        super(message);
    }
}
