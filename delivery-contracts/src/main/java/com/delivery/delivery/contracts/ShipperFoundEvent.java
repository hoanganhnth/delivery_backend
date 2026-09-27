package com.delivery.delivery.contracts;

import com.delivery.identity.contracts.SimulationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Immutable matching result shared by matching, delivery, and notification. */
public record ShipperFoundEvent(
        UUID eventId,
        Long deliveryId,
        Long orderId,
        List<ShipperMatchResult> availableShippers,
        LocalDateTime foundAt,
        Integer waitingTimeoutSeconds,
        String matchingSessionId,
        String restaurantName,
        String pickupAddress,
        String deliveryAddress,
        Double pickupLat,
        Double pickupLng,
        Double deliveryLat,
        Double deliveryLng,
        BigDecimal totalPrice,
        String paymentMethod,
        SimulationContext simulationContext,
        Boolean batchOffer,
        UUID batchId,
        List<BatchItem> batchItems,
        List<UUID> codHoldIds,
        Integer batchWave) {

    public record BatchItem(
            Long deliveryId,
            Long orderId,
            Integer pickupSequence,
            Integer dropoffSequence,
            BigDecimal totalPrice,
            UUID matchingSessionId) {
    }

    public record ShipperMatchResult(
            Long shipperId,
            String shipperName,
            String shipperPhone,
            Double distanceKm,
            Double latitude,
            Double longitude,
            Double rating,
            Boolean isOnline) {
    }
}
