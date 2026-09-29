package com.delivery.delivery.contracts;

import com.delivery.identity.contracts.SimulationContext;
import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable canonical delivery status event shared by producers and notification consumers. */
public record DeliveryStatusUpdatedEvent(
        UUID eventId,
        String eventType,
        LocalDateTime eventTimestamp,
        Long deliveryId,
        Long orderId,
        Long userId,
        Long userPrincipalId,
        Long shipperId,
        String status,
        String previousStatus,
        String shipperName,
        SimulationContext simulationContext) {
    public DeliveryStatusUpdatedEvent {
        simulationContext = SimulationContext.orReal(simulationContext);
        simulationContext.requireValid();
    }
}
