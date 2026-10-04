package com.delivery.settlement.application.api.refund;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerRefundCase(
        UUID refundId,
        Long orderId,
        String paymentMethod,
        String trigger,
        String status,
        String currency,
        BigDecimal refundAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime processedAt) {}
