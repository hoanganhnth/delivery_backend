package com.delivery.settlement.application;

import com.delivery.settlement.application.api.PaymentProviderPort;
import com.delivery.settlement.application.api.PaymentUseCase;
import com.delivery.settlement.domain.payment.PaymentOperation;
import com.delivery.settlement.domain.payment.PaymentOperationRequest;
import com.delivery.settlement.domain.payment.ProviderOperationResult;
import java.util.Objects;

/** Framework-free payment orchestration. Provider adapters remain behind the port. */
public final class DefaultPaymentService implements PaymentUseCase {
    private final PaymentProviderPort provider;

    public DefaultPaymentService(PaymentProviderPort provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public ProviderOperationResult create(PaymentOperationRequest request) {
        return invoke(request, PaymentOperation.CREATE, provider::create);
    }

    @Override
    public ProviderOperationResult refund(PaymentOperationRequest request) {
        return invoke(request, PaymentOperation.REFUND, provider::refund);
    }

    @Override
    public ProviderOperationResult status(PaymentOperationRequest request) {
        return invoke(request, PaymentOperation.STATUS, provider::status);
    }

    private ProviderOperationResult invoke(PaymentOperationRequest request,
            PaymentOperation expected, java.util.function.Function<PaymentOperationRequest,
                    ProviderOperationResult> operation) {
        Objects.requireNonNull(request, "request");
        if (request.operation() != expected) {
            throw new IllegalArgumentException("request operation must be " + expected);
        }
        try {
            ProviderOperationResult result = operation.apply(request);
            return result == null ? ProviderOperationResult.unknown("provider returned no result") : result;
        } catch (RuntimeException exception) {
            return ProviderOperationResult.unknown("provider operation outcome is unknown");
        }
    }
}
