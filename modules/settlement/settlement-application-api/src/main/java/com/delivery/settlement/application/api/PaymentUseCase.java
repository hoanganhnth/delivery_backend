package com.delivery.settlement.application.api;

import com.delivery.settlement.domain.payment.PaymentOperationRequest;
import com.delivery.settlement.domain.payment.ProviderOperationResult;

/** Application boundary for provider-neutral payment operations. */
public interface PaymentUseCase {
    ProviderOperationResult create(PaymentOperationRequest request);

    ProviderOperationResult refund(PaymentOperationRequest request);

    ProviderOperationResult status(PaymentOperationRequest request);
}
