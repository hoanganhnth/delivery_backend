package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.inventory.InventoryOrderSource;
import java.util.UUID;

public record InventoryOrderEventCommand(UUID eventId, long orderId, UUID reservationId,
        String sourceTopic, InventoryOrderSource source, String fingerprint) { }
