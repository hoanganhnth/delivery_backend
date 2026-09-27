package com.delivery.delivery.contracts;

import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable command that expires one shipper-offer generation. */
public record ExpireShipperOfferCommand(
        UUID eventId,
        Long orderId,
        Long deliveryId,
        Long timedOutShipperId,
        LocalDateTime expectedOfferExpiresAt,
        String matchingSessionId) {
}
