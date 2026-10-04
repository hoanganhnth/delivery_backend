package com.delivery.settlement_service.payment;

import com.delivery.settlement.application.api.PaymentWorkflowDependencies.*;
import com.delivery.settlement.domain.payment.WorkflowPayment;
import com.delivery.settlement_service.entity.*;
import com.delivery.settlement_service.payment.dto.*;
import com.delivery.settlement_service.repository.PaymentOrderRepository;
import com.delivery.settlement_service.service.*;
import java.util.*;

/** JPA/provider/ledger/event operations for the framework-free payment workflow. */
public final class PaymentWorkflowAdapters implements Store, Providers, Effects {
    // One adapter is created for each host invocation; retain the original managed JPA object.
    private final Map<WorkflowPayment, PaymentOrder> managed = new IdentityHashMap<>();
    private final PaymentOrderRepository repository;
    private final PaymentProviderRegistry registry;
    private final TransactionService transactions;
    private final PaymentEventPublisher events;
    public PaymentWorkflowAdapters(PaymentOrderRepository repository, PaymentProviderRegistry registry,
            TransactionService transactions, PaymentEventPublisher events) {
        this.repository = repository; this.registry = registry; this.transactions = transactions; this.events = events;
    }
    @Override public Optional<WorkflowPayment> byId(Long id) { return repository.findById(id).map(this::state); }
    @Override public Optional<WorkflowPayment> byReference(String ref) { return repository.findByPaymentRef(ref).map(this::state); }
    @Override public void save(WorkflowPayment payment) {
        PaymentOrder saved = repository.save(entity(payment));
        if (saved != null) {
            managed.put(payment, saved);
            payment.setId(saved.getId()); payment.setCreatedAt(saved.getCreatedAt());
        }
    }
    @Override public Provider get(String name) {
        PaymentProvider provider = registry.getProvider(name);
        return new Provider() {
            @Override public Creation create(Request request) {
                PaymentResult result = provider.createPayment(PaymentRequest.builder().paymentRef(request.reference())
                        .amount(request.amount()).currency(request.currency()).orderInfo(request.orderInfo())
                        .returnUrl(request.returnUrl()).ipAddress(request.ipAddress()).locale(request.locale()).build());
                return new Creation(result.isSuccess(), result.getPaymentUrl(), result.getProviderTransactionId(), result.getErrorMessage());
            }
            @Override public Verification verify(Map<String, String> parameters) {
                PaymentVerifyResult result = provider.verifyPayment(parameters);
                return new Verification(result.isVerified(), result.isPaymentSuccess(), result.getPaymentRef(),
                        result.getProviderTransactionId(), result.getRawPayload(), result.getAmount(), result.getMessage());
            }
        };
    }
    @Override public Set<String> available() { return registry.getAvailableProviders(); }
    @Override public Long topUp(WorkflowPayment payment) {
        return transactions.topUpDeposit(payment.getEntityId(), EntityType.valueOf(payment.getEntityType()),
                payment.getAmount(), payment.getProvider()).getId();
    }
    @Override public void success(WorkflowPayment payment) { events.publishPaymentSuccess(entity(payment)); }
    @Override public void failed(WorkflowPayment payment, String reason) { events.publishPaymentFailed(entity(payment), reason); }
    private PaymentOrder entity(WorkflowPayment value) {
        PaymentOrder target = managed.computeIfAbsent(value, ignored -> new PaymentOrder());
        target.setId(value.getId());
        target.setPaymentRef(value.getPaymentRef());
        target.setEntityId(value.getEntityId());
        target.setEntityType(EntityType.valueOf(value.getEntityType()));
        target.setOrderId(value.getOrderId());
        target.setProvider(value.getProvider());
        target.setAmount(value.getAmount());
        target.setCurrency(value.getCurrency());
        target.setPurpose(PaymentOrder.PaymentPurpose.valueOf(value.getPurpose()));
        target.setStatus(PaymentOrder.PaymentStatus.valueOf(value.getStatus()));
        target.setPaymentUrl(value.getPaymentUrl());
        target.setProviderTransactionId(value.getProviderTransactionId());
        target.setCallbackPayload(value.getCallbackPayload());
        target.setSettlementTransactionId(value.getSettlementTransactionId());
        target.setReturnUrl(value.getReturnUrl());
        target.setIpAddress(value.getIpAddress());
        target.setCreatedAt(value.getCreatedAt());
        target.setExpiredAt(value.getExpiredAt());
        return target;
    }
    private WorkflowPayment state(PaymentOrder value) {
        WorkflowPayment target = new WorkflowPayment();
        managed.put(target, value);
        target.setId(value.getId());
        target.setPaymentRef(value.getPaymentRef());
        target.setEntityId(value.getEntityId());
        target.setEntityType(value.getEntityType().name());
        target.setOrderId(value.getOrderId());
        target.setProvider(value.getProvider());
        target.setAmount(value.getAmount());
        target.setCurrency(value.getCurrency());
        target.setPurpose(value.getPurpose().name());
        target.setStatus(value.getStatus().name());
        target.setPaymentUrl(value.getPaymentUrl());
        target.setProviderTransactionId(value.getProviderTransactionId());
        target.setCallbackPayload(value.getCallbackPayload());
        target.setSettlementTransactionId(value.getSettlementTransactionId());
        target.setReturnUrl(value.getReturnUrl());
        target.setIpAddress(value.getIpAddress());
        target.setCreatedAt(value.getCreatedAt());
        target.setExpiredAt(value.getExpiredAt());
        return target;
    }
}
