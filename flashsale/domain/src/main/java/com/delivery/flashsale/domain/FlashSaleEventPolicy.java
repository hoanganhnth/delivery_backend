package com.delivery.flashsale.domain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class FlashSaleEventPolicy {
    private FlashSaleEventPolicy() { }
    public record Receipt(String sourceTopic, String action, Long orderId, UUID reservationId, String fingerprint) { }
    public static String canonicalSourceTopic(String topic) {
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("source topic is required");
        return topic.replaceFirst("-retry-flashsale-\\d+$", "");
    }
    public static String actionFor(String source, String created, String cancelled, String refund) {
        if (created.equals(source)) return "COMMIT";
        if (cancelled.equals(source) || refund.equals(source)) return "RELEASE";
        throw new IllegalArgumentException("Unexpected flash-sale reservation source topic: " + source);
    }
    public static void requireExactReplay(Receipt stored, Receipt incoming) {
        if (!stored.sourceTopic().equals(incoming.sourceTopic()) || !stored.action().equals(incoming.action())
                || !stored.orderId().equals(incoming.orderId())
                || !Objects.equals(stored.reservationId(), incoming.reservationId())
                || !stored.fingerprint().equals(incoming.fingerprint()))
            throw new IllegalArgumentException("eventId replay has a contradictory flash-sale reservation payload");
    }
    public static String eventType(String state) { return "FLASH_SALE_RESERVATION_" + state; }
    public static UUID eventId(UUID reservationId, String eventType) {
        return UUID.nameUUIDFromBytes((reservationId + ":" + eventType).getBytes(StandardCharsets.UTF_8));
    }
    public static boolean dead(int attempts) { return attempts >= 12; }
    public static long retrySeconds(int attempts) { return Math.min(300, 1L << Math.min(attempts, 8)); }
    public static String lastError(String message) {
        String value = message == null ? "Kafka publish failed" : message;
        return value.substring(0, Math.min(2000, value.length()));
    }
}
