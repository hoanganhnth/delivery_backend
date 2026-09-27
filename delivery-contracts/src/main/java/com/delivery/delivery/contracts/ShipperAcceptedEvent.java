package com.delivery.delivery.contracts;

import com.delivery.identity.contracts.SimulationContext;

/** Immutable command/result announcing a shipper acceptance. */
public record ShipperAcceptedEvent(
        Long orderId,
        Long deliveryId,
        Long shipperId,
        String notes,
        SimulationContext simulationContext) {
}
