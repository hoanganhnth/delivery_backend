package com.delivery.order.contracts;

import com.delivery.identity.contracts.SimulationContext;
import java.time.LocalDateTime;

/** Immutable shipper decision event consumed by order. */
public record ShipperEvent(
        Long shipperId,
        Long deliveryId,
        Long orderId,
        String action,
        String notes,
        String rejectReason,
        LocalDateTime responseTime,
        Double estimatedPickupTime,
        Double currentLat,
        Double currentLng,
        SimulationContext simulationContext) {
}
