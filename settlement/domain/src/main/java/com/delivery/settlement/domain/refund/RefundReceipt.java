package com.delivery.settlement.domain.refund;

import java.util.Objects;
import java.util.UUID;

public record RefundReceipt(UUID refundId, UUID eventId, String idempotencyKey, Long orderId,
        RefundPolicy.Trigger trigger, String fingerprint) {
    public void requireExact(UUID event, Long order, String key, String hash, boolean dispute) {
        if (!Objects.equals(idempotencyKey,key) || !Objects.equals(orderId,order) || !Objects.equals(eventId,event)
                || (dispute && trigger != RefundPolicy.Trigger.DELIVERY_DISPUTE) || !Objects.equals(fingerprint,hash)) {
            throw new IllegalArgumentException(dispute ? "delivery exception refund replay has a contradictory payload"
                    : "refund event replay has a contradictory payload");
        }
    }
}
