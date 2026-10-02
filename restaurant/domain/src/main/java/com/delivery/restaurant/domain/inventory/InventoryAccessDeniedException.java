package com.delivery.restaurant.domain.inventory;

public final class InventoryAccessDeniedException extends RuntimeException {
    public InventoryAccessDeniedException(String message) { super(message); }
}
