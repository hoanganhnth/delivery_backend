package com.delivery.settlement_service.payment.contract;

/**
 * Safe provider result. Raw callbacks, signatures, secrets and card data are
 * deliberately not part of this contract; an adapter may retain a masked
 * diagnostic in its own audited boundary.
 */
public record ProviderOperationResult(
        ProviderOperationStatus status,
        String providerReference,
        String message,
        boolean retryable) {

    public ProviderOperationResult {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (providerReference != null && providerReference.isBlank()) {
            throw new IllegalArgumentException("providerReference cannot be blank");
        }
        if (message != null && message.length() > 2000) {
            throw new IllegalArgumentException("message exceeds the audited limit");
        }
        if ((status == ProviderOperationStatus.SUCCEEDED
                || status == ProviderOperationStatus.PARTIAL)
                && (providerReference == null || providerReference.isBlank())) {
            throw new IllegalArgumentException("successful provider result requires providerReference");
        }
    }

    public static ProviderOperationResult unknown(String message) {
        return new ProviderOperationResult(ProviderOperationStatus.UNKNOWN, null, message, true);
    }

    public static ProviderOperationResult failed(String message, boolean retryable) {
        return new ProviderOperationResult(ProviderOperationStatus.FAILED, null, message, retryable);
    }
}
