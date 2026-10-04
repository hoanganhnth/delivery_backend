package com.delivery.settlement_service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.PaymentOrder;
import com.delivery.settlement_service.entity.PaymentOrder.PaymentPurpose;
import com.delivery.settlement_service.entity.PaymentOrder.PaymentStatus;
import com.delivery.settlement_service.payment.dto.PaymentVerifyResult;
import com.delivery.settlement_service.payment.provider.VnPayProvider;
import com.delivery.settlement_service.repository.PaymentOrderRepository;
import com.delivery.settlement_service.service.PaymentEventPublisher;
import com.delivery.settlement_service.service.TransactionService;
import com.delivery.settlement_service.service.impl.PaymentServiceImpl;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentCallbackIdempotencyTest {

    @Test
    void successCallbackMapsOnceAndDoesNotPublishDuplicateEffects() {
        PaymentOrder order = pendingOrder("PAY-success");
        PaymentVerifyResult verifiedSuccess = PaymentVerifyResult.success("PAY-success", "txn-1", "payload");
        verifiedSuccess.setAmount(10_000_000L);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentProviderRegistry registry = mock(PaymentProviderRegistry.class);
        PaymentOrderRepository repository = mock(PaymentOrderRepository.class);
        PaymentEventPublisher events = mock(PaymentEventPublisher.class);
        when(registry.getProvider("VNPAY")).thenReturn(provider);
        when(provider.verifyPayment(Map.of())).thenReturn(verifiedSuccess);
        when(repository.findByPaymentRefForUpdate("PAY-success")).thenReturn(Optional.of(order));

        PaymentServiceImpl service = new PaymentServiceImpl(
                repository, registry, mock(TransactionService.class), events);
        service.handleCallback("VNPAY", Map.of());
        service.handleCallback("VNPAY", Map.of());

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(events).publishPaymentSuccess(order);
    }

    @Test
    void cancellationCallbackMapsOnceAndDoesNotPublishDuplicateEffects() {
        PaymentOrder order = pendingOrder("PAY-cancel");
        PaymentVerifyResult cancelled = PaymentVerifyResult.failed("PAY-cancel", "24", "Cancelled", "payload");
        cancelled.setAmount(10_000_000L);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentProviderRegistry registry = mock(PaymentProviderRegistry.class);
        PaymentOrderRepository repository = mock(PaymentOrderRepository.class);
        PaymentEventPublisher events = mock(PaymentEventPublisher.class);
        when(registry.getProvider("VNPAY")).thenReturn(provider);
        when(provider.verifyPayment(Map.of())).thenReturn(cancelled);
        when(repository.findByPaymentRefForUpdate("PAY-cancel")).thenReturn(Optional.of(order));

        PaymentServiceImpl service = new PaymentServiceImpl(
                repository, registry, mock(TransactionService.class), events);
        service.handleCallback("VNPAY", Map.of());
        service.handleCallback("VNPAY", Map.of());

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(events).publishPaymentFailed(order, "Cancelled");
    }

    @Test
    void missingVnPayCredentialsFailClosedForCreateAndVerification() {
        VnPayProvider provider = new VnPayProvider();
        ReflectionTestUtils.setField(provider, "tmnCode", "");
        ReflectionTestUtils.setField(provider, "hashSecret", "");

        assertThat(provider.createPayment(com.delivery.settlement_service.payment.dto.PaymentRequest.builder()
                .paymentRef("PAY-credentials")
                .amount(new BigDecimal("100000"))
                .currency("VND")
                .returnUrl("delivery://payments/vnpay-return")
                .build()).isSuccess()).isFalse();
        assertThat(provider.verifyPayment(Map.of()).isVerified()).isFalse();
    }

    @Test
    void amountMismatchAndTerminalReplayKeepOriginalLogMessagesAndLevels() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PaymentWorkflowAdapters.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            var order = pendingOrder("PAY-logs");
            var repository = mock(PaymentOrderRepository.class);
            var registry = mock(PaymentProviderRegistry.class);
            var provider = mock(PaymentProvider.class);
            when(registry.getProvider("VNPAY")).thenReturn(provider);
            when(repository.findByPaymentRefForUpdate("PAY-logs")).thenReturn(Optional.of(order));
            var result = PaymentVerifyResult.success("PAY-logs", "tx", "raw");
            result.setAmount(1L);
            when(provider.verifyPayment(Map.of())).thenReturn(result);
            var service = new PaymentServiceImpl(repository, registry, mock(TransactionService.class), mock(PaymentEventPublisher.class));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.handleCallback("VNPAY", Map.of()))
                    .isInstanceOf(SecurityException.class);
            order.setStatus(PaymentStatus.SUCCESS);
            service.handleCallback("VNPAY", Map.of());
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
                assertThat(event.getFormattedMessage()).isEqualTo("🚨 Amount mismatch! ref=PAY-logs, expected=10000000, received=1");
            }).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
                assertThat(event.getFormattedMessage()).isEqualTo("⏭️ Payment already processed: ref=PAY-logs, status=SUCCESS");
            });
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    private PaymentOrder pendingOrder(String paymentRef) {
        return PaymentOrder.builder()
                .id(1L)
                .paymentRef(paymentRef)
                .entityId(77L)
                .entityType(EntityType.SHIPPER)
                .provider("VNPAY")
                .amount(new BigDecimal("100000"))
                .purpose(PaymentPurpose.ORDER_PAYMENT)
                .status(PaymentStatus.PENDING)
                .build();
    }
}
