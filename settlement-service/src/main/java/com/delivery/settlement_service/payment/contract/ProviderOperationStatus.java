package com.delivery.settlement_service.payment.contract;

/**
 * Provider-neutral terminal/intermediate states shared by payment, refund and
 * payout adapters. UNKNOWN is intentionally distinct from FAILED: a timeout
 * must never be interpreted as money movement having failed or succeeded.
 */
public enum ProviderOperationStatus {
    REQUESTED,
    PROCESSING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    UNKNOWN,
    MANUAL_REVIEW
}
