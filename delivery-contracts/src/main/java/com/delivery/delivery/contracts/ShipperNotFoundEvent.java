package com.delivery.delivery.contracts;

import com.delivery.identity.contracts.SimulationContext;
import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable terminal matching outcome shared by matching, delivery, and order. */
public record ShipperNotFoundEvent(
        UUID eventId,
        Long deliveryId,
        Long orderId,
        String matchingSessionId,
        String reason,
        LocalDateTime occurredAt,
        Integer retryAttempts,
        Double searchRadius,
        Double pickupLat,
        Double pickupLng,
        Double deliveryLat,
        Double deliveryLng,
        SimulationContext simulationContext) {
}
