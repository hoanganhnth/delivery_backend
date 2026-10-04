package com.delivery.settlement.application;

import com.delivery.settlement.application.api.*;
import com.delivery.settlement.application.api.PaymentWorkflowDependencies.*;
import com.delivery.settlement.domain.payment.WorkflowPayment;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultPaymentWorkflowTest {
    private final Fixture fixture = new Fixture();
    private final DefaultPaymentWorkflow core = new DefaultPaymentWorkflow(fixture, fixture, fixture,
            Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC), () -> "PAY-1", "VNPAY", 15, "return-default");
    private PaymentWorkflowCommand command(String type, String purpose, String provider, String url, String ip) {
        return new PaymentWorkflowCommand(22L, 91L, type, new BigDecimal("100.99"), provider, purpose, url, ip);
    }
    @Test void createPreservesDefaultsProviderInputAndPersistenceMetadata() {
        var result = core.create(command(null, null, null, null, null));
        assertThat(result.entityType()).isEqualTo("SHIPPER");
        assertThat(result.purpose()).isEqualTo("DEPOSIT_TOPUP");
        assertThat(result.currency()).isEqualTo("VND");
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(result.expiredAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 19, 5));
        assertThat(result.paymentUrl()).isEqualTo("url");
        assertThat(result.providerTransactionId()).isEqualTo("provider-id");
        assertThat(fixture.request).isEqualTo(new Request("PAY-1", new BigDecimal("100.99"), "VND",
                "Nap tien ky quy - PAY-1", "return-default", "127.0.0.1", "vn"));
        assertThat(fixture.calls).containsExactly("get:VNPAY", "save:PENDING", "create", "save:PENDING");
        assertThat(fixture.payment.getReturnUrl()).isNull();
        assertThat(fixture.payment.getIpAddress()).isNull();
        assertThat(fixture.payment.getOrderId()).isEqualTo(91L);
        assertThat(core.byId(1L)).isEqualTo(result);
        assertThat(core.byReference("PAY-1")).isEqualTo(result);
        assertThat(core.availableProviders()).containsExactly("VNPAY", "FAKE");
    }
    @Test void createUsesExplicitValuesAndRetainsInvalidEnumFallbacks() {
        core.create(command("invalid", "invalid", "fake", "custom", "ip"));
        assertThat(fixture.payment.getEntityType()).isEqualTo("SHIPPER");
        assertThat(fixture.payment.getPurpose()).isEqualTo("DEPOSIT_TOPUP");
        assertThat(fixture.payment.getProvider()).isEqualTo("FAKE");
        assertThat(fixture.request.returnUrl()).isEqualTo("custom");
        assertThat(fixture.request.ipAddress()).isEqualTo("ip");
    }
    @Test void failedCreateWritesFailureThenThrowsAndProviderExceptionsPropagate() {
        fixture.creation = new Creation(false, null, null, "declined");
        assertThatThrownBy(() -> core.create(command("SYSTEM", "WITHDRAWAL", null, null, null)))
                .hasMessage("Payment creation failed: declined");
        assertThat(fixture.calls).endsWith("save:FAILED");
        assertThat(fixture.payment.getCallbackPayload()).isEqualTo("declined");
        fixture.throwCreate = true;
        assertThatThrownBy(() -> core.create(command(null, null, null, null, null))).hasMessage("timeout");
        assertThat(fixture.payment.getStatus()).isEqualTo("PENDING");
    }
    @Test void signatureIsVerifiedBeforeReadAndMissingReadsRemainFailures() {
        fixture.verification = new Verification(false, false, null, null, null, null, "bad");
        assertThatThrownBy(() -> core.callback("VNPAY", Map.of())).isInstanceOf(SecurityException.class);
        assertThat(fixture.calls).containsExactly("get:VNPAY", "verify");
        assertThatThrownBy(() -> core.byId(9L)).hasMessage("Payment order not found: 9");
        assertThatThrownBy(() -> core.byReference("absent")).hasMessage("Payment order not found: absent");
        fixture.verification = new Verification(true, true, "absent", null, null, null, "ok");
        assertThatThrownBy(() -> core.callback("VNPAY", Map.of())).hasMessage("Payment order not found: absent");
    }
    @Test void successTopupPrecedesEventAndFinalSaveWhileReplayHasNoEffects() {
        core.create(command("restaurant", "deposit_topup", null, null, null)); fixture.calls.clear();
        fixture.verification = new Verification(true, true, "PAY-1", "callback-id", "raw", 10000L, "ok");
        var result = core.callback("VNPAY", Map.of());
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.settlementTransactionId()).isEqualTo(88L);
        assertThat(result.providerTransactionId()).isEqualTo("callback-id");
        assertThat(fixture.payment.getCallbackPayload()).isEqualTo("raw");
        assertThat(fixture.calls).containsExactly("get:VNPAY", "verify", "read:PAY-1", "topup", "success", "save:SUCCESS");
        fixture.calls.clear(); fixture.verification = new Verification(true, false, "PAY-1", null, null, 1L, "later");
        assertThat(core.callback("VNPAY", Map.of())).isEqualTo(result);
        assertThat(fixture.calls).containsExactly("get:VNPAY", "verify", "read:PAY-1");
    }
    @Test void failureSavesBeforeEventAndMismatchWritesThenThrowsWithoutEffects() {
        core.create(command(null, "ORDER_PAYMENT", null, null, null)); fixture.calls.clear();
        fixture.verification = new Verification(true, false, "PAY-1", null, "cancelled", null, "cancel");
        assertThat(core.callback("VNPAY", Map.of()).status()).isEqualTo("FAILED");
        assertThat(fixture.calls).containsExactly("get:VNPAY", "verify", "read:PAY-1", "save:FAILED", "failed:cancel");
        core.create(command(null, "ORDER_PAYMENT", null, null, null)); fixture.calls.clear();
        fixture.verification = new Verification(true, true, "PAY-1", null, "raw", 10099L, "ok");
        assertThatThrownBy(() -> core.callback("VNPAY", Map.of())).isInstanceOf(SecurityException.class).hasMessage("Payment amount mismatch");
        assertThat(fixture.calls).containsExactly("get:VNPAY", "verify", "read:PAY-1", "save:FAILED");
        assertThat(fixture.payment.getCallbackPayload()).isEqualTo("Amount mismatch: 10099");
    }
    @Test void fakeRequiresProviderEvenOnTerminalPaymentsAndDoesNotInventOrderOrWithdrawalLedgerEffects() {
        core.create(command("shipper", "order_payment", "FAKE", null, null)); fixture.calls.clear();
        assertThat(core.confirmFake("PAY-1").status()).isEqualTo("SUCCESS");
        assertThat(fixture.calls).containsExactly("read:PAY-1", "success", "save:SUCCESS");
        assertThat(fixture.payment.getCallbackPayload()).isEqualTo("{\"provider\":\"FAKE\",\"status\":\"SUCCESS\"}");
        fixture.calls.clear(); core.confirmFake("PAY-1");
        assertThat(fixture.calls).containsExactly("read:PAY-1");
        fixture.payment.setProvider("VNPAY");
        assertThatThrownBy(() -> core.confirmFake("PAY-1")).isInstanceOf(IllegalArgumentException.class);
        core.create(command("system", "withdrawal", "FAKE", null, null)); fixture.calls.clear();
        core.confirmFake("PAY-1"); assertThat(fixture.calls).doesNotContain("topup");
        fixture.payment.setStatus("EXPIRED"); fixture.calls.clear();
        core.callback("VNPAY", Map.of()); assertThat(fixture.calls).doesNotContain("success", "topup");
    }
    private static final class Fixture implements Store, Providers, Provider, Effects {
        WorkflowPayment payment; Request request; boolean throwCreate;
        Creation creation = new Creation(true, "url", "provider-id", null);
        Verification verification = new Verification(true, true, "PAY-1", null, "raw", null, "ok");
        final List<String> calls = new ArrayList<>();
        public Optional<WorkflowPayment> byId(Long id) { return Optional.ofNullable(payment).filter(p -> id.equals(p.getId())); }
        public Optional<WorkflowPayment> byReference(String ref) { calls.add("read:" + ref); return Optional.ofNullable(payment).filter(p -> ref.equals(p.getPaymentRef())); }
        public void save(WorkflowPayment value) { calls.add("save:" + value.getStatus()); payment = value; value.setId(1L); value.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4)); }
        public Provider get(String name) { calls.add("get:" + name); return this; }
        public Set<String> available() { return new LinkedHashSet<>(List.of("VNPAY", "FAKE")); }
        public Creation create(Request value) { calls.add("create"); request = value; if (throwCreate) throw new RuntimeException("timeout"); return creation; }
        public Verification verify(Map<String, String> params) { calls.add("verify"); return verification; }
        public Long topUp(WorkflowPayment value) { calls.add("topup"); return 88L; }
        public void success(WorkflowPayment value) { calls.add("success"); }
        public void failed(WorkflowPayment value, String reason) { calls.add("failed:" + reason); }
    }
}
