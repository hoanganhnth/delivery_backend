package com.delivery.settlement_service.payment.contract;

/**
 * Narrow seam for a future PayOS HTTP/signature client. This interface has no
 * Spring annotation and no network implementation in the MVP; tests may use
 * an in-process fixture to prove mapping and idempotency only.
 */
public interface PayOsClient {

    ProviderOperationResult createPayment(PaymentOperationRequest request);

    ProviderOperationResult refund(PaymentOperationRequest request);

    ProviderOperationResult status(PaymentOperationRequest request);

    ProviderOperationResult submitPayout(PayoutRequest request);

    ProviderOperationResult payoutStatus(PayoutRequest request);
}
