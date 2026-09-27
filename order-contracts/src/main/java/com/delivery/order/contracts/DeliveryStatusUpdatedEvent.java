package com.delivery.order.contracts;

import com.delivery.identity.contracts.SimulationContext;
import java.time.LocalDateTime;

/** Immutable delivery status snapshot consumed by order. */
public record DeliveryStatusUpdatedEvent(
        Long deliveryId,
        Long orderId,
        Long shipperId,
        String status,
        String previousStatus,
        String newStatus,
        String oldStatus,
        LocalDateTime updatedAt,
        LocalDateTime timestamp,
        String eventType,
        String notes,
        Double currentLat,
        Double currentLng,
        LocalDateTime estimatedDeliveryTime,
        SimulationContext simulationContext) {
}
