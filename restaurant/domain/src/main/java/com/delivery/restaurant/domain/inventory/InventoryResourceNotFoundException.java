package com.delivery.restaurant.domain.inventory;

public class InventoryResourceNotFoundException extends RuntimeException {

    public InventoryResourceNotFoundException(String message) {
        super(message);
    }
}
