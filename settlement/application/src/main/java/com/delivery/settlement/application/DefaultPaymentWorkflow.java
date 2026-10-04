package com.delivery.settlement.application;

import com.delivery.settlement.application.api.*;
import com.delivery.settlement.application.api.PaymentWorkflowDependencies.*;
import com.delivery.settlement.domain.payment.WorkflowPayment;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Existing payment workflow. Transactions are supplied by the calling host adapter. */
public final class DefaultPaymentWorkflow implements PaymentWorkflowPort {
    private final Store store;
    private final Providers providers;
    private final Effects effects;
    private final Clock clock;
    private final Supplier<String> references;
    private final String defaultProvider;
    private final int expiryMinutes;
    private final String defaultReturnUrl;

    public DefaultPaymentWorkflow(Store store, Providers providers, Effects effects, Clock clock,
            Supplier<String> references, String defaultProvider, int expiryMinutes, String defaultReturnUrl) {
        this.store = store; this.providers = providers; this.effects = effects; this.clock = clock;
        this.references = references; this.defaultProvider = defaultProvider;
        this.expiryMinutes = expiryMinutes; this.defaultReturnUrl = defaultReturnUrl;
    }
    @Override public PaymentWorkflowResult create(PaymentWorkflowCommand command) {
        String name = command.provider() == null ? defaultProvider : command.provider();
        Provider provider = providers.get(name);
        WorkflowPayment payment = new WorkflowPayment();
        payment.setPaymentRef(references.get()); payment.setEntityId(command.entityId());
        payment.setEntityType(WorkflowPayment.entityType(command.entityType()));
        payment.setOrderId(command.orderId()); payment.setProvider(name.toUpperCase());
        payment.setAmount(command.amount()); payment.setCurrency("VND");
        payment.setPurpose(WorkflowPayment.purpose(command.purpose())); payment.setStatus("PENDING");
        payment.setReturnUrl(command.returnUrl()); payment.setIpAddress(command.ipAddress());
        payment.setExpiredAt(LocalDateTime.now(clock).plusMinutes(expiryMinutes));
        store.save(payment);
        Creation result = provider.create(new Request(payment.getPaymentRef(), command.amount(), "VND",
                "Nap tien ky quy - " + payment.getPaymentRef(),
                command.returnUrl() == null ? defaultReturnUrl : command.returnUrl(),
                command.ipAddress() == null ? "127.0.0.1" : command.ipAddress(), "vn"));
        if (!result.success()) {
            payment.setStatus("FAILED"); payment.setCallbackPayload(result.error()); store.save(payment);
            // Runtime failure intentionally rolls back both writes at the host transaction boundary.
            throw new RuntimeException("Payment creation failed: " + result.error());
        }
        payment.setPaymentUrl(result.paymentUrl()); payment.setProviderTransactionId(result.transactionId());
        store.save(payment);
        return result(payment);
    }
    @Override public PaymentWorkflowResult callback(String provider, Map<String, String> parameters) {
        Verification verification = providers.get(provider).verify(parameters);
        if (!verification.verified()) throw new SecurityException("Invalid payment callback signature");
        WorkflowPayment payment = find(verification.reference());
        if (!payment.isPending()) return result(payment);
        if (!payment.matchesAmount(verification.amount())) {
            payment.setStatus("FAILED"); payment.setCallbackPayload("Amount mismatch: " + verification.amount());
            store.save(payment);
            throw new SecurityException("Payment amount mismatch");
        }
        payment.setCallbackPayload(verification.payload());
        payment.setProviderTransactionId(verification.transactionId());
        if (verification.success()) return succeed(payment);
        payment.setStatus("FAILED"); store.save(payment);
        effects.failed(payment, verification.message());
        return result(payment);
    }
    @Override public PaymentWorkflowResult confirmFake(String reference) {
        WorkflowPayment payment = find(reference); payment.requireFake();
        if (!payment.isPending()) return result(payment);
        payment.setCallbackPayload("{\"provider\":\"FAKE\",\"status\":\"SUCCESS\"}");
        return succeed(payment);
    }
    @Override public PaymentWorkflowResult byId(Long id) {
        return result(store.byId(id).orElseThrow(() -> new RuntimeException("Payment order not found: " + id)));
    }
    @Override public PaymentWorkflowResult byReference(String reference) { return result(find(reference)); }
    @Override public Set<String> availableProviders() { return providers.available(); }
    private WorkflowPayment find(String reference) {
        return store.byReference(reference).orElseThrow(() -> new RuntimeException("Payment order not found: " + reference));
    }
    private PaymentWorkflowResult succeed(WorkflowPayment payment) {
        payment.setStatus("SUCCESS");
        if (payment.isTopUp()) payment.setSettlementTransactionId(effects.topUp(payment));
        effects.success(payment);
        store.save(payment);
        return result(payment);
    }
    private PaymentWorkflowResult result(WorkflowPayment payment) {
        return new PaymentWorkflowResult(payment.getId(), payment.getPaymentRef(), payment.getEntityId(),
                payment.getEntityType(), payment.getProvider(), payment.getAmount(), payment.getCurrency(),
                payment.getPurpose(), payment.getStatus(), payment.getPaymentUrl(), payment.getProviderTransactionId(),
                payment.getSettlementTransactionId(), payment.getCreatedAt(), payment.getExpiredAt());
    }
}
