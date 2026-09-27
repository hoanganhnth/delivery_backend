package com.delivery.delivery.contracts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable post-pickup delivery exception event. */
public record DeliveryExceptionReportedEvent(
        UUID eventId,
        String eventType,
        LocalDateTime occurredAt,
        UUID exceptionId,
        Long deliveryId,
        Long orderId,
        Long userId,
        Long userPrincipalId,
        Long restaurantId,
        Long shipperId,
        String previousDeliveryStatus,
        String currentDeliveryStatus,
        String exceptionStatus,
        String reason,
        String paymentMethod,
        BigDecimal subtotalPrice,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal totalPrice) {
}
