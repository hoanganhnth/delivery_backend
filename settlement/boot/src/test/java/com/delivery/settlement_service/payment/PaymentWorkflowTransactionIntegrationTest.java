package com.delivery.settlement_service.payment;

import com.delivery.settlement.application.api.*;
import com.delivery.settlement_service.entity.*;
import com.delivery.settlement_service.payment.dto.*;
import com.delivery.settlement_service.repository.*;
import com.delivery.settlement_service.service.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual Spring proxy/JPA proof: attempted FAILED writes roll back on runtime exceptions. */
@SpringBootTest(properties = {"app.payment.processing-enabled=true", "spring.task.scheduling.enabled=false"})
@ActiveProfiles("test")
class PaymentWorkflowTransactionIntegrationTest {
    @Autowired PaymentWorkflowPort workflow;
    @Autowired PaymentService legacy;
    @Autowired PaymentOrderRepository payments;
    @Autowired TransactionRepository transactions;
    @Autowired BalanceRepository balances;
    @MockBean PaymentProviderRegistry registry;
    @MockBean PaymentEventPublisher events;
    @org.springframework.boot.test.mock.mockito.SpyBean TransactionService ledger;
    PaymentProvider provider;
    @BeforeEach void setup() {
        payments.deleteAll(); transactions.deleteAll(); balances.deleteAll();
        provider = mock(PaymentProvider.class);
        when(registry.getProvider(anyString())).thenReturn(provider);
        when(provider.createPayment(any())).thenReturn(PaymentResult.success("url", "create-id"));
    }
    @AfterEach void cleanup() { payments.deleteAll(); transactions.deleteAll(); balances.deleteAll(); }
    PaymentWorkflowCommand command(String purpose) {
        return new PaymentWorkflowCommand(22L, 91L, "SHIPPER", new BigDecimal("100.99"), "VNPAY", purpose, null, null);
    }
    PaymentVerifyResult callbackResult(String ref, boolean success, Long amount) {
        var result = success ? PaymentVerifyResult.success(ref, "callback-id", "raw")
                : PaymentVerifyResult.failed(ref, "24", "cancel", "raw");
        result.setAmount(amount); when(provider.verifyPayment(anyMap())).thenReturn(result); return result;
    }
    @Test void createFailureAndProviderExceptionLeaveNoPaymentRows() {
        when(provider.createPayment(any())).thenReturn(PaymentResult.failure("declined"));
        assertThatThrownBy(() -> workflow.create(command("DEPOSIT_TOPUP"))).hasMessage("Payment creation failed: declined");
        assertThat(payments.count()).isZero();
        when(provider.createPayment(any())).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> workflow.create(command("DEPOSIT_TOPUP"))).hasMessage("timeout");
        assertThat(payments.count()).isZero(); verifyNoInteractions(events);
    }
    @Test void amountMismatchRollsBackAttemptedFailureMetadataAndLaterCallbackCanSucceed() {
        var created = workflow.create(command("ORDER_PAYMENT"));
        assertThat(created.createdAt()).isNotNull(); assertThat(created.expiredAt()).isAfter(created.createdAt());
        callbackResult(created.paymentReference(), true, 10099L);
        assertThatThrownBy(() -> workflow.callback("VNPAY", Map.of())).isInstanceOf(SecurityException.class);
        var unchanged = payments.findById(created.id()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(PaymentOrder.PaymentStatus.PENDING);
        assertThat(unchanged.getCallbackPayload()).isNull();
        assertThat(unchanged.getProviderTransactionId()).isEqualTo("create-id");
        callbackResult(created.paymentReference(), true, 10000L);
        var success = workflow.callback("VNPAY", Map.of());
        assertThat(success.status()).isEqualTo("SUCCESS");
        assertThat(success.createdAt()).isEqualTo(created.createdAt());
        assertThat(transactions.count()).isZero(); assertThat(balances.count()).isZero();
        workflow.callback("VNPAY", Map.of());
        verify(events, times(1)).publishPaymentSuccess(any());
        assertThat(legacy.getPaymentStatus(created.id()).getPaymentRef()).isEqualTo(created.paymentReference());
        assertThat(legacy.getPaymentByRef(created.paymentReference()).getStatus()).isEqualTo("SUCCESS");
    }
    @Test void topupCommitsOnceButEventFailureRollsBackPaymentLedgerAndWalletMutation() {
        var created = workflow.create(command("DEPOSIT_TOPUP")); callbackResult(created.paymentReference(), true, null);
        doThrow(new IllegalStateException("event failed")).when(events).publishPaymentSuccess(any());
        assertThatThrownBy(() -> workflow.callback("VNPAY", Map.of())).hasMessage("event failed");
        assertThat(payments.findById(created.id()).orElseThrow().getStatus()).isEqualTo(PaymentOrder.PaymentStatus.PENDING);
        assertThat(transactions.count()).isZero();
        // Legacy account creation is REQUIRES_NEW: its zero account survives; financial mutation rolls back.
        assertThat(balances.count()).isEqualTo(1);
        assertThat(balances.findByEntityIdAndEntityType(22L, EntityType.SHIPPER).orElseThrow().getDepositBalance()).isEqualByComparingTo("0");
        doNothing().when(events).publishPaymentSuccess(any());
        var success = workflow.callback("VNPAY", Map.of());
        assertThat(success.settlementTransactionId()).isNotNull();
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(balances.findByEntityIdAndEntityType(22L, EntityType.SHIPPER).orElseThrow().getDepositBalance()).isEqualByComparingTo("100.99");
        workflow.callback("VNPAY", Map.of()); assertThat(transactions.count()).isEqualTo(1);
    }
    @Test void topUpFailureBeforePublicationRollsBackOriginalPaymentMetadata() {
        var created = workflow.create(command("DEPOSIT_TOPUP"));
        callbackResult(created.paymentReference(), true, null);
        doThrow(new IllegalStateException("ledger unavailable")).when(ledger).topUpDeposit(anyLong(), any(), any(), any());
        assertThatThrownBy(() -> workflow.callback("VNPAY", Map.of())).hasMessage("ledger unavailable");
        assertOriginalPending(created.id());
        assertThat(transactions.count()).isZero(); verifyNoInteractions(events);
    }
    @Test void failedPublicationRollsBackFailureMetadata() {
        var created = workflow.create(command("ORDER_PAYMENT"));
        callbackResult(created.paymentReference(), false, null);
        doThrow(new IllegalStateException("failed publication")).when(events).publishPaymentFailed(any(), any());
        assertThatThrownBy(() -> workflow.callback("VNPAY", Map.of())).hasMessage("failed publication");
        assertOriginalPending(created.id()); assertThat(transactions.count()).isZero();
    }
    @Test void fakeTopUpCommitsExactlyOnceAndReplayHasNoEffects() {
        var created = workflow.create(new PaymentWorkflowCommand(22L, null, "SHIPPER", BigDecimal.TEN,
                "FAKE", "DEPOSIT_TOPUP", null, null));
        var success = workflow.confirmFake(created.paymentReference());
        assertThat(success.status()).isEqualTo("SUCCESS");
        assertThat(success.settlementTransactionId()).isNotNull();
        assertThat(workflow.confirmFake(created.paymentReference())).isEqualTo(success);
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(balances.findByEntityIdAndEntityType(22L, EntityType.SHIPPER).orElseThrow().getDepositBalance())
                .isEqualByComparingTo("10");
        verify(events, times(1)).publishPaymentSuccess(any()); verify(events, never()).publishPaymentFailed(any(), any());
    }
    private void assertOriginalPending(Long id) {
        var persisted = payments.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PaymentOrder.PaymentStatus.PENDING);
        assertThat(persisted.getCallbackPayload()).isNull();
        assertThat(persisted.getProviderTransactionId()).isEqualTo("create-id");
        assertThat(persisted.getSettlementTransactionId()).isNull();
    }
    @Test void verifiedCancellationCommitsAndFakeEndpointRetainsProviderRestriction() {
        var created = workflow.create(command("ORDER_PAYMENT")); callbackResult(created.paymentReference(), false, null);
        assertThat(workflow.callback("VNPAY", Map.of()).status()).isEqualTo("FAILED");
        assertThat(payments.findById(created.id()).orElseThrow().getCallbackPayload()).isEqualTo("raw");
        workflow.callback("VNPAY", Map.of()); verify(events).publishPaymentFailed(any(), eq("cancel"));
        assertThatThrownBy(() -> legacy.confirmFakePayment(created.paymentReference())).isInstanceOf(IllegalArgumentException.class);
        var fake = workflow.create(new PaymentWorkflowCommand(22L, null, null, BigDecimal.TEN, "FAKE", "ORDER_PAYMENT", null, null));
        assertThat(legacy.confirmFakePayment(fake.paymentReference()).getStatus()).isEqualTo("SUCCESS");
        assertThat(workflow.confirmFake(fake.paymentReference()).status()).isEqualTo("SUCCESS");
    }
}
