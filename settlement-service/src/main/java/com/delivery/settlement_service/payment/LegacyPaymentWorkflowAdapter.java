package com.delivery.settlement_service.payment;

import com.delivery.settlement.application.api.PaymentWorkflowCommand;
import com.delivery.settlement.application.api.PaymentWorkflowPort;
import com.delivery.settlement.application.api.PaymentWorkflowResult;
import com.delivery.settlement_service.dto.request.CreatePaymentRequest;
import com.delivery.settlement_service.dto.response.PaymentOrderResponse;
import com.delivery.settlement_service.service.PaymentService;
import java.util.Map;

/** Host adapter preserving the existing payment workflow while exposing application API results. */
public final class LegacyPaymentWorkflowAdapter implements PaymentWorkflowPort {
    private final PaymentService service;
    private final PaymentProviderRegistry providers;

    public LegacyPaymentWorkflowAdapter(PaymentService service, PaymentProviderRegistry providers) {
        this.service = service;
        this.providers = providers;
    }

    @Override public PaymentWorkflowResult create(PaymentWorkflowCommand command) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setEntityId(command.entityId()); request.setOrderId(command.orderId());
        request.setEntityType(command.entityType()); request.setAmount(command.amount());
        request.setProvider(command.provider()); request.setPurpose(command.purpose());
        request.setReturnUrl(command.returnUrl()); request.setIpAddress(command.ipAddress());
        return map(service.createPayment(request));
    }
    @Override public PaymentWorkflowResult callback(String provider, Map<String, String> parameters) {
        return map(service.handleCallback(provider, parameters));
    }
    @Override public PaymentWorkflowResult byId(Long paymentId) { return map(service.getPaymentStatus(paymentId)); }
    @Override public PaymentWorkflowResult byReference(String paymentReference) { return map(service.getPaymentByRef(paymentReference)); }
    @Override public java.util.Set<String> availableProviders() { return providers.getAvailableProviders(); }

    private static PaymentWorkflowResult map(PaymentOrderResponse value) {
        return new PaymentWorkflowResult(value.getId(), value.getPaymentRef(), value.getEntityId(), value.getEntityType(),
                value.getProvider(), value.getAmount(), value.getCurrency(), value.getPurpose(), value.getStatus(),
                value.getPaymentUrl(), value.getProviderTransactionId(), value.getSettlementTransactionId(),
                value.getCreatedAt(), value.getExpiredAt());
    }
}
