package com.delivery.settlement.application.api;

import java.math.BigDecimal;

public record PaymentWorkflowCommand(Long entityId, Long orderId, String entityType,
        BigDecimal amount, String provider, String purpose, String returnUrl, String ipAddress) {}
