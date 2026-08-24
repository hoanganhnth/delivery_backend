package com.delivery.settlement_service.payment.contract;

import java.util.Objects;

/**
 * Contract-level PayOS adapter. It delegates only to an explicitly supplied
 * client port and is intentionally not a Spring bean: no credentials, HTTP
 * client, callback route or provider call can appear in the default runtime.
 */
public final class PayOsProviderAdapter implements PaymentProviderPort, PayoutProviderPort {

    public static final String PROVIDER_NAME = "PAYOS";

    private final PayOsClient client;

    public PayOsProviderAdapter(PayOsClient client) {
        this.client = Objects.requireNonNull(client, "PayOS client is required");
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public ProviderOperationResult create(PaymentOperationRequest request) {
        requireOperation(request, PaymentOperation.CREATE);
        return client.createPayment(request);
    }

    @Override
    public ProviderOperationResult refund(PaymentOperationRequest request) {
        requireOperation(request, PaymentOperation.REFUND);
        return client.refund(request);
    }

    @Override
    public ProviderOperationResult status(PaymentOperationRequest request) {
        requireOperation(request, PaymentOperation.STATUS);
        return client.status(request);
    }

    @Override
    public ProviderOperationResult submit(PayoutRequest request) {
        return client.submitPayout(request);
    }

    @Override
    public ProviderOperationResult status(PayoutRequest request) {
        return client.payoutStatus(request);
    }

    private void requireOperation(PaymentOperationRequest request, PaymentOperation expected) {
        if (request == null || request.operation() != expected) {
            throw new IllegalArgumentException("PayOS request operation must be " + expected);
        }
    }
}
