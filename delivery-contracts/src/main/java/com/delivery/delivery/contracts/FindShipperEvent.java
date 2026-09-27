package com.delivery.delivery.contracts;

import com.delivery.identity.contracts.SimulationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Immutable command requesting a shipper search. */
public record FindShipperEvent(
        UUID eventId,
        Long deliveryId,
        Long orderId,
        String restaurantName,
        String pickupAddress,
        Double pickupLat,
        Double pickupLng,
        String deliveryAddress,
        Double deliveryLat,
        Double deliveryLng,
        LocalDateTime estimatedDeliveryTime,
        String notes,
        LocalDateTime createdAt,
        BigDecimal totalPrice,
        String paymentMethod,
        String eventType,
        LocalDateTime timestamp,
        LocalDateTime occurredAt,
        Integer maxRetryAttempts,
        Integer initialDelaySeconds,
        Integer maxDelaySeconds,
        Double backoffMultiplier,
        LocalDateTime matchingDeadlineAt,
        UUID matchingSessionId,
        List<Long> excludedShipperIds,
        Boolean batchOfferEnabled,
        Integer batchWave,
        SimulationContext simulationContext) {
}
