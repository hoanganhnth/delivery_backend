package com.delivery.settlement_service.payment.contract;

/**
 * Payout follows the payment/provider boundary but stays separately gated.
 * Implementations must be idempotent and must never infer success from a
 * timeout.
 */
public interface PayoutProviderPort {

    String providerName();

    ProviderOperationResult submit(PayoutRequest request);

    ProviderOperationResult status(PayoutRequest request);
}
