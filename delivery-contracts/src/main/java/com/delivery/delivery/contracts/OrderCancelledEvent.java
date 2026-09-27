package com.delivery.delivery.contracts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable order-cancelled snapshot used by delivery and compensation consumers. */
public record OrderCancelledEvent(
        Integer schemaVersion,
        UUID eventId,
        String eventType,
        LocalDateTime occurredAt,
        Long orderId,
        Long userId,
        Long userPrincipalId,
        Long restaurantId,
        String previousStatus,
        String currentStatus,
        String cancelReason,
        Long cancelledBy,
        String cancelledBySource,
        String cancelReasonCode,
        LocalDateTime cancelledAt,
        Long shipperId,
        Boolean hasActiveDelivery,
        UUID voucherReservationId,
        UUID promotionReservationId,
        UUID flashSaleReservationId,
        UUID inventoryReservationId,
        List<Map<String, Object>> items,
        List<Map<String, Object>> appliedVouchers,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        BigDecimal subtotalPrice,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal totalPrice,
        BigDecimal itemDiscount,
        BigDecimal shippingDiscount,
        BigDecimal customerShippingFee,
        BigDecimal grossShippingFee,
        BigDecimal platformSubsidy,
        BigDecimal shopDiscount,
        String paymentMethod) {
}
