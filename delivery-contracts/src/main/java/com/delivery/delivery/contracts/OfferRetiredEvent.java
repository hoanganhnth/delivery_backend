package com.delivery.delivery.contracts;

import java.util.UUID;

/** Immutable confirmation that an expired or abandoned offer was retired. */
public record OfferRetiredEvent(
        UUID eventId,
        UUID sourceCommandEventId,
        Long orderId,
        Long deliveryId,
        String matchingSessionId,
        String outcome,
        Long shipperId) {
}
