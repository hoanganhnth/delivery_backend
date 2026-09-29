package com.delivery.settlement.application;

import com.delivery.settlement.application.api.PayoutProviderPort;
import com.delivery.settlement.application.api.PayoutUseCase;
import com.delivery.settlement.domain.payment.PayoutRequest;
import com.delivery.settlement.domain.payment.ProviderOperationResult;
import java.util.Objects;
import java.util.function.Function;

/** Framework-free payout orchestration with timeout-safe provider semantics. */
public final class DefaultPayoutService implements PayoutUseCase {
    private final PayoutProviderPort provider;

    public DefaultPayoutService(PayoutProviderPort provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public ProviderOperationResult submit(PayoutRequest request) {
        return invoke(request, provider::submit);
    }

    @Override
    public ProviderOperationResult status(PayoutRequest request) {
        return invoke(request, provider::status);
    }

    private ProviderOperationResult invoke(PayoutRequest request,
            Function<PayoutRequest, ProviderOperationResult> operation) {
        Objects.requireNonNull(request, "request");
        try {
            ProviderOperationResult result = operation.apply(request);
            return result == null ? ProviderOperationResult.unknown("provider returned no result") : result;
        } catch (RuntimeException exception) {
            return ProviderOperationResult.unknown("provider operation outcome is unknown");
        }
    }
}
