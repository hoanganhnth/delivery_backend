package com.delivery.order.contracts;

import java.time.LocalDateTime;

/** Immutable payment lifecycle event shared with order and settlement. */
public record PaymentEvent(
        Long paymentId,
        Long orderId,
        Long userId,
        String status,
        Double amount,
        String paymentMethod,
        String transactionId,
        LocalDateTime processedAt,
        String failureReason) {
}
