package com.delivery.settlement.application.api;

import com.delivery.settlement.domain.payment.PayoutRequest;
import com.delivery.settlement.domain.payment.ProviderOperationResult;

/** Application boundary for provider-neutral payout operations. */
public interface PayoutUseCase {
    ProviderOperationResult submit(PayoutRequest request);

    ProviderOperationResult status(PayoutRequest request);
}
