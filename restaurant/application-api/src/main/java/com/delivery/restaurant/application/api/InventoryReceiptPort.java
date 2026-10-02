package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.inventory.InventoryOrderAction;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReceiptPort {
    record Receipt(UUID eventId, String sourceTopic, InventoryOrderAction action,
            Long orderId, UUID reservationId, String fingerprint) { }
    int insertIfAbsent(Receipt receipt);
    Optional<Receipt> find(UUID eventId);
}
