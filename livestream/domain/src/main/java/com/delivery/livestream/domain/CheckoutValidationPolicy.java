package com.delivery.livestream.domain;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Existing checkout validation, independent of transport and persistence. */
public final class CheckoutValidationPolicy {
    private static final int MAX_PRODUCTS = 50;

    private CheckoutValidationPolicy() { }

    public static void requireScope(UUID livestreamId, Long restaurantId, List<Long> productIds) {
        if (livestreamId == null || restaurantId == null || restaurantId <= 0
                || productIds == null || productIds.isEmpty() || productIds.size() > MAX_PRODUCTS
                || productIds.stream().anyMatch(id -> id == null || id <= 0)
                || new LinkedHashSet<>(productIds).size() != productIds.size()) {
            throw new IllegalArgumentException("Invalid livestream checkout quote scope");
        }
    }

    public static void requireContextMetadata(Long actorPrincipalId, String correlationId, String idempotencyKey) {
        if (actorPrincipalId == null || actorPrincipalId <= 0 || correlationId == null || correlationId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Livestream checkout context requires actor, correlation and idempotency key");
        }
    }

    public static void requireQuoteProduct(Long restaurantId, Long productRestaurantId, BigDecimal priceAtLive) {
        if (!restaurantId.equals(productRestaurantId)) {
            throw new IllegalStateException("Pinned product restaurant scope is invalid");
        }
        if (priceAtLive == null || priceAtLive.signum() <= 0) {
            throw new IllegalStateException("Pinned product price is invalid");
        }
    }

    public static void requireContextProduct(Long snapshotId, BigDecimal priceAtLive,
                                             Long restaurantId, Long productRestaurantId) {
        if (snapshotId == null || priceAtLive == null || priceAtLive.signum() <= 0
                || !restaurantId.equals(productRestaurantId)) {
            throw new IllegalStateException("Pinned product snapshot is invalid");
        }
    }
}
