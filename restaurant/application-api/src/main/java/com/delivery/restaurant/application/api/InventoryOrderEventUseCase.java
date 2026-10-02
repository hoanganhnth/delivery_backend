package com.delivery.restaurant.application.api;

public interface InventoryOrderEventUseCase {
    void consume(InventoryOrderEventCommand command);
}
