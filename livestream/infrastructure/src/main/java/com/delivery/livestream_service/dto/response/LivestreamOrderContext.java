package com.delivery.livestream_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Stable, versioned handoff contract consumed by order checkout. */
public record LivestreamOrderContext(
        int schemaVersion,
        UUID streamId,
        Long pinnedProductId,
        Long sellerId,
        Long restaurantId,
        Long priceSnapshotId,
        Long actorPrincipalId,
        String correlationId,
        String idempotencyKey,
        BigDecimal priceAtLive) {
}
