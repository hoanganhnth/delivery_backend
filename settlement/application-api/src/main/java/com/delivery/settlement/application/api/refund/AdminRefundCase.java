package com.delivery.settlement.application.api.refund;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminRefundCase(
        UUID refundId,
        UUID eventId,
        String idempotencyKey,
        Long orderId,
        Long userId,
        Long userPrincipalId,
        Long restaurantId,
        String previousOrderStatus,
        String currentOrderStatus,
        String paymentMethod,
        String trigger,
        String component,
        String status,
        String currency,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        BigDecimal capturedAmount,
        BigDecimal refundAmount,
        String actorSource,
        Long actorId,
        String reason,
        String providerReference,
        String lastError,
        int attempts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime processedAt) {}
