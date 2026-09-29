package com.delivery.settlement.application;

import com.delivery.settlement.application.api.PaymentProviderPort;
import com.delivery.settlement.application.api.PaymentUseCase;
import com.delivery.settlement.application.api.PayoutProviderPort;
import com.delivery.settlement.application.api.PayoutUseCase;
import com.delivery.settlement.domain.payment.PaymentOperationRequest;
import com.delivery.settlement.domain.payment.PayoutRequest;
import com.delivery.settlement.domain.payment.ProviderOperationResult;

/** Unified settlement facade for hosts that expose both payment and payout flows. */
public final class DefaultSettlementService implements PaymentUseCase, PayoutUseCase {
    private final DefaultPaymentService payments;
    private final DefaultPayoutService payouts;

    public DefaultSettlementService(PaymentProviderPort paymentProvider, PayoutProviderPort payoutProvider) {
        this.payments = new DefaultPaymentService(paymentProvider);
        this.payouts = new DefaultPayoutService(payoutProvider);
    }

    @Override public ProviderOperationResult create(PaymentOperationRequest request) { return payments.create(request); }
    @Override public ProviderOperationResult refund(PaymentOperationRequest request) { return payments.refund(request); }
    @Override public ProviderOperationResult status(PaymentOperationRequest request) { return payments.status(request); }
    @Override public ProviderOperationResult submit(PayoutRequest request) { return payouts.submit(request); }
    @Override public ProviderOperationResult status(PayoutRequest request) { return payouts.status(request); }
}
