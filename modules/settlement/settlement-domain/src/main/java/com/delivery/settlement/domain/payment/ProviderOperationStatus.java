package com.delivery.settlement.domain.payment;

/** UNKNOWN is distinct from FAILED because a timeout is not a money-movement result. */
public enum ProviderOperationStatus {
    REQUESTED,
    PROCESSING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    UNKNOWN,
    MANUAL_REVIEW
}
