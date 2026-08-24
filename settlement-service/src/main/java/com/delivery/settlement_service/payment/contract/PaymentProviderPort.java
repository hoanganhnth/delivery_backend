package com.delivery.settlement_service.payment.contract;

/**
 * Unwired provider-neutral port. Registering an implementation is a separate
 * rollout step; the existing legacy PaymentProvider graph remains hidden by
 * app.payment.processing-enabled.
 */
public interface PaymentProviderPort {

    String providerName();

    ProviderOperationResult create(PaymentOperationRequest request);

    ProviderOperationResult refund(PaymentOperationRequest request);

    ProviderOperationResult status(PaymentOperationRequest request);
}
