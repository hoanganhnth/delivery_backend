package com.delivery.settlement_service.service.impl;

import com.delivery.settlement.application.DefaultPaymentWorkflow;
import com.delivery.settlement.application.api.*;
import com.delivery.settlement_service.dto.request.CreatePaymentRequest;
import com.delivery.settlement_service.dto.response.PaymentOrderResponse;
import com.delivery.settlement_service.payment.PaymentProviderRegistry;
import com.delivery.settlement_service.payment.PaymentWorkflowAdapters;
import com.delivery.settlement_service.repository.PaymentOrderRepository;
import com.delivery.settlement_service.service.*;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction and legacy DTO adapter; all payment decisions run in the application core. */
@Service
@ConditionalOnProperty(name = "app.payment.processing-enabled", havingValue = "true")
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService, PaymentWorkflowPort {
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentProviderRegistry providerRegistry;
    private final TransactionService transactionService;
    private final PaymentEventPublisher paymentEventPublisher;
    @Value("${payment.default-provider:VNPAY}") private String defaultProvider;
    @Value("${payment.order-expiry-minutes:15}") private int orderExpiryMinutes;
    @Value("${payment.vnpay.return-url:http://localhost:8095/api/settlement/payments/vnpay-callback}")
    private String defaultReturnUrl;

    private DefaultPaymentWorkflow core() {
        PaymentWorkflowAdapters adapters = new PaymentWorkflowAdapters(paymentOrderRepository, providerRegistry,
                transactionService, paymentEventPublisher);
        return new DefaultPaymentWorkflow(adapters, adapters, adapters, Clock.systemDefaultZone(),
                () -> "PAY-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                defaultProvider, orderExpiryMinutes, defaultReturnUrl);
    }
    @Override @Transactional public PaymentWorkflowResult create(PaymentWorkflowCommand command) { return core().create(command); }
    @Override @Transactional public PaymentWorkflowResult callback(String provider, Map<String, String> parameters) { return core().callback(provider, parameters); }
    @Override @Transactional(readOnly = true) public PaymentWorkflowResult byId(Long id) { return core().byId(id); }
    @Override @Transactional(readOnly = true) public PaymentWorkflowResult byReference(String ref) { return core().byReference(ref); }
    @Override public Set<String> availableProviders() { return core().availableProviders(); }
    @Override @Transactional public PaymentWorkflowResult confirmFake(String ref) { return core().confirmFake(ref); }
    @Override @Transactional public PaymentOrderResponse createPayment(CreatePaymentRequest request) {
        return response(core().create(new PaymentWorkflowCommand(request.getEntityId(), request.getOrderId(),
                request.getEntityType(), request.getAmount(), request.getProvider(), request.getPurpose(),
                request.getReturnUrl(), request.getIpAddress())));
    }
    @Override @Transactional public PaymentOrderResponse handleCallback(String provider, Map<String, String> parameters) { return response(core().callback(provider, parameters)); }
    @Override @Transactional public PaymentOrderResponse confirmFakePayment(String ref) { return response(core().confirmFake(ref)); }
    @Override @Transactional(readOnly = true) public PaymentOrderResponse getPaymentStatus(Long id) { return response(core().byId(id)); }
    @Override @Transactional(readOnly = true) public PaymentOrderResponse getPaymentByRef(String ref) { return response(core().byReference(ref)); }
    private PaymentOrderResponse response(PaymentWorkflowResult result) {
        return PaymentOrderResponse.builder().id(result.id()).paymentRef(result.paymentReference())
                .entityId(result.entityId()).entityType(result.entityType()).provider(result.provider())
                .amount(result.amount()).currency(result.currency()).purpose(result.purpose()).status(result.status())
                .paymentUrl(result.paymentUrl()).providerTransactionId(result.providerTransactionId())
                .settlementTransactionId(result.settlementTransactionId()).createdAt(result.createdAt())
                .expiredAt(result.expiredAt()).build();
    }
}
