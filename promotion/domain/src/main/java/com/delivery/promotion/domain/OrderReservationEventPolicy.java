package com.delivery.promotion.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class OrderReservationEventPolicy {
    private OrderReservationEventPolicy() {}
    public record Topic(String source, String action, String failure) {}
    public enum Operation { NONE, COMMIT_LEGACY, COMMIT_BULK, RELEASE_LEGACY, RELEASE_BULK }
    public record Receipt(String source, String action, Long orderId, UUID reservationId, String fingerprint) {}
    public static Topic topic(String received, String created, String cancelled, String refund) {
        if (received == null || received.isBlank()) return new Topic(null, null, "source topic is required");
        String source = received.replaceFirst("-retry-promotion-\\d+$", "");
        if (created.equals(source)) return new Topic(source, "COMMIT", null);
        if (cancelled.equals(source) || refund.equals(source)) return new Topic(source, "RELEASE", null);
        return new Topic(source, null, "Unexpected voucher reservation source topic: " + source);
    }
    public static Operation operation(String action, UUID legacy, UUID bulk, String previousStatus) {
        if (legacy == null && bulk == null) return Operation.NONE;
        if ("COMMIT".equals(action)) return bulk != null ? Operation.COMMIT_BULK : Operation.COMMIT_LEGACY;
        String previous = previousStatus.toUpperCase(Locale.ROOT);
        if ("PICKED_UP".equals(previous) || "DELIVERING".equals(previous)
                || "DELIVERED".equals(previous) || "COMPLETED".equals(previous)) return Operation.NONE;
        return bulk != null ? Operation.RELEASE_BULK : Operation.RELEASE_LEGACY;
    }
    public static String commitFailure(String state, boolean bulk) {
        return "COMMITTED".equals(state) ? null : (bulk ? "Promotion" : "Voucher")
                + " reservation did not reach COMMITTED state";
    }
    public static String replayFailure(Receipt stored, Receipt received) {
        return !stored.source().equals(received.source()) || !stored.action().equals(received.action())
                || !stored.orderId().equals(received.orderId())
                || !Objects.equals(stored.reservationId(), received.reservationId())
                || !stored.fingerprint().equals(received.fingerprint())
                ? "eventId replay has a contradictory voucher reservation payload" : null;
    }
}
