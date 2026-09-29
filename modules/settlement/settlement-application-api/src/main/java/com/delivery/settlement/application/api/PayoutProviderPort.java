package com.delivery.settlement.application.api;

import com.delivery.settlement.domain.payment.PayoutRequest;
import com.delivery.settlement.domain.payment.ProviderOperationResult;

/** Provider boundary for idempotent payout submission and status checks. */
public interface PayoutProviderPort {
    String providerName();
    ProviderOperationResult submit(PayoutRequest request);
    ProviderOperationResult status(PayoutRequest request);
}
