package com.delivery.settlement.application.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentWorkflowResult(Long id, String paymentReference, Long entityId, String entityType,
        String provider, BigDecimal amount, String currency, String purpose, String status,
        String paymentUrl, String providerTransactionId, Long settlementTransactionId,
        LocalDateTime createdAt, LocalDateTime expiredAt) {}
