package com.delivery.settlement.domain.refund;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable persistence intent; transport-only event fields stay in the fingerprint adapter. */
public record RefundDraft(UUID refundId, UUID eventId, String idempotencyKey, Long orderId, Long userId,
        Long userPrincipalId, Long restaurantId, String previousStatus, String currentStatus, String paymentMethod,
        RefundPolicy.Decision decision, BigDecimal subtotal, BigDecimal discount, BigDecimal shipping,
        BigDecimal total, Long actorId, String reason, String fingerprint) {
    public static String key(Long orderId, RefundPolicy.Trigger trigger) { return orderId+":"+trigger.name()+":ORDER_TOTAL"; }
    public RefundReceipt receipt() {
        return new RefundReceipt(refundId,eventId,idempotencyKey,orderId,decision.trigger(),fingerprint);
    }
    public static RefundDraft cancellation(UUID id, RefundPolicy.Cancellation e, String hash, boolean providerEnabled) {
        var decision=RefundPolicy.decide(e,providerEnabled);
        return new RefundDraft(id,e.eventId(),key(e.orderId(),decision.trigger()),e.orderId(),e.userId(),
                e.userPrincipalId(),e.restaurantId(),e.previousStatus(),e.currentStatus(),e.paymentMethod(),decision,
                e.subtotalPrice(),e.discountAmount(),e.shippingFee(),e.totalPrice(),e.cancelledBy(),e.cancelReason(),hash);
    }
    public static RefundDraft deliveryException(UUID id, RefundPolicy.DeliveryException e, String hash) {
        var decision=RefundPolicy.decide(e);
        return new RefundDraft(id,e.eventId(),key(e.orderId(),decision.trigger()),e.orderId(),e.userId(),
                e.userPrincipalId(),e.restaurantId(),e.previousDeliveryStatus(),e.currentDeliveryStatus(),e.paymentMethod(),
                decision,e.subtotalPrice(),e.discountAmount(),e.shippingFee(),e.totalPrice(),e.shipperId(),e.reason(),hash);
    }
}
