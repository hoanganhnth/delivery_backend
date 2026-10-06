package com.delivery.analytics.domain;
import java.math.BigDecimal;
import java.util.Objects;
/** Immutable accepted identity; ingest time and persistence ID are deliberately excluded. */
public record ReceiptIdentity(String eventType, Long orderId, Long userId, Long restaurantId,
        String restaurantName, BigDecimal amount, String orderStatus, String paymentMethod,
        Long aggregateVersion, String rawPayload, String payloadFingerprint) {
    public void requireExactReplay(ReceiptIdentity incoming) {
        boolean payloadMatches = payloadFingerprint != null
                ? payloadFingerprint.equals(incoming.payloadFingerprint)
                : Objects.equals(rawPayload, incoming.rawPayload);
        if (!eventType.equals(incoming.eventType)
                || !Objects.equals(orderId, incoming.orderId)
                || !Objects.equals(userId, incoming.userId)
                || !Objects.equals(restaurantId, incoming.restaurantId)
                || !Objects.equals(restaurantName, incoming.restaurantName)
                || !sameAmount(amount, incoming.amount)
                || !Objects.equals(orderStatus, incoming.orderStatus)
                || !Objects.equals(paymentMethod, incoming.paymentMethod)
                || !Objects.equals(aggregateVersion, incoming.aggregateVersion) || !payloadMatches) {
            throw new IllegalArgumentException("analytics deduplication key replay has contradictory identity or payload");
        }
    }
    private static boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
