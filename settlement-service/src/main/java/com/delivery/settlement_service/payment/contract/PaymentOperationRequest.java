package com.delivery.settlement_service.payment.contract;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutral payment/refund command. The operation ID is stable across
 * retries; idempotencyKey is the durable business key and must not be replaced
 * by a timestamp or a newly generated value on provider retry.
 */
public record PaymentOperationRequest(
        UUID operationId,
        String idempotencyKey,
        String merchantReference,
        Long orderId,
        MoneyAmount amount,
        String paymentMethod,
        PaymentOperation operation,
        String originalProviderReference,
        Map<String, String> metadata) {

    public PaymentOperationRequest {
        requireUuid(operationId, "operationId");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(merchantReference, "merchantReference");
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(operation, "operation is required");
        if (!"ONLINE".equalsIgnoreCase(paymentMethod)) {
            throw new IllegalArgumentException("provider payment operations require ONLINE paymentMethod");
        }
        paymentMethod = paymentMethod.trim().toUpperCase(java.util.Locale.ROOT);
        if (operation == PaymentOperation.REFUND) {
            requireText(originalProviderReference, "originalProviderReference");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static void requireUuid(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
