package com.delivery.settlement.application.api;

import com.delivery.settlement.domain.payment.PaymentOperationRequest;
import com.delivery.settlement.domain.payment.ProviderOperationResult;

/** Provider boundary for payment, refund, and status operations. */
public interface PaymentProviderPort {
    String providerName();
    ProviderOperationResult create(PaymentOperationRequest request);
    ProviderOperationResult refund(PaymentOperationRequest request);
    ProviderOperationResult status(PaymentOperationRequest request);
}
