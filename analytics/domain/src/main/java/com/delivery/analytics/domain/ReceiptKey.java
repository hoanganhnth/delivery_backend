package com.delivery.analytics.domain;

/** Event IDs are namespaced by event type; legacy identity falls back to order ID. */
public final class ReceiptKey {
    private ReceiptKey() {}

    public static String resolve(String eventType, Long orderId, String textualEventId) {
        if (textualEventId != null && !textualEventId.isBlank()) {
            return eventType + ":event:" + textualEventId;
        }
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Analytics event requires a positive orderId");
        }
        return eventType + ":order:" + orderId;
    }
}
