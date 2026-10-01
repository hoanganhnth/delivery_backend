package com.delivery.analytics_service.service;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import java.math.BigDecimal;
import java.util.Objects;

/** Compares accepted receipt identity without repository or projection side effects. */
final class AnalyticsReplayPolicy {
    private AnalyticsReplayPolicy() {}

    static void requireExactReplay(AnalyticsEvent existing, AnalyticsEvent incoming) {
        boolean payloadMatches = existing.getPayloadFingerprint() != null
                ? existing.getPayloadFingerprint().equals(incoming.getPayloadFingerprint())
                : Objects.equals(existing.getRawPayload(), incoming.getRawPayload());
        if (!existing.getEventType().equals(incoming.getEventType())
                || !Objects.equals(existing.getOrderId(), incoming.getOrderId())
                || !Objects.equals(existing.getUserId(), incoming.getUserId())
                || !Objects.equals(existing.getRestaurantId(), incoming.getRestaurantId())
                || !Objects.equals(existing.getRestaurantName(), incoming.getRestaurantName())
                || !sameAmount(existing.getAmount(), incoming.getAmount())
                || !Objects.equals(existing.getOrderStatus(), incoming.getOrderStatus())
                || !Objects.equals(existing.getPaymentMethod(), incoming.getPaymentMethod())
                || !Objects.equals(existing.getAggregateVersion(), incoming.getAggregateVersion())
                || !payloadMatches) {
            throw new IllegalArgumentException(
                    "analytics deduplication key replay has contradictory identity or payload");
        }
    }

    private static boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
