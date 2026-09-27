package com.delivery.delivery.contracts;

import com.delivery.identity.contracts.SimulationContext;
import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable confirmation that a shipper offer was persisted. */
public record OfferPersistedEvent(
        UUID eventId,
        UUID sourceCommandEventId,
        Long orderId,
        Long deliveryId,
        String matchingSessionId,
        Long offeredShipperId,
        LocalDateTime offerExpiresAt,
        String status,
        SimulationContext simulationContext) {
}
