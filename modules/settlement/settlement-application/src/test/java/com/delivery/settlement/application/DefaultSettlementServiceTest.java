package com.delivery.settlement.application;

import com.delivery.settlement.application.api.PaymentProviderPort;
import com.delivery.settlement.application.api.PayoutProviderPort;
import com.delivery.settlement.domain.EntityType;
import com.delivery.settlement.domain.payment.MoneyAmount;
import com.delivery.settlement.domain.payment.PaymentOperation;
import com.delivery.settlement.domain.payment.PaymentOperationRequest;
import com.delivery.settlement.domain.payment.PayoutRequest;
import com.delivery.settlement.domain.payment.ProviderOperationResult;
import com.delivery.settlement.domain.payment.ProviderOperationStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultSettlementServiceTest {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void paymentDelegatesEachOperationAndPreservesResult() {
        var provider = new RecordingPaymentProvider();
        var service = new DefaultPaymentService(provider);
        var success = new ProviderOperationResult(ProviderOperationStatus.SUCCEEDED, "pay-1", null, false);

        assertThat(service.create(payment(PaymentOperation.CREATE))).isEqualTo(success);
        assertThat(service.refund(payment(PaymentOperation.REFUND))).isEqualTo(success);
        assertThat(service.status(payment(PaymentOperation.STATUS))).isEqualTo(success);
        assertThat(provider.calls).containsExactly("create", "refund", "status");
    }

    @Test
    void providerFailureAndNullResultBecomeUnknown() {
        var failing = new DefaultPaymentService(new RecordingPaymentProvider() {
            @Override public ProviderOperationResult create(PaymentOperationRequest request) {
                throw new IllegalStateException("timeout");
            }
            @Override public ProviderOperationResult status(PaymentOperationRequest request) { return null; }
        });

        assertThat(failing.create(payment(PaymentOperation.CREATE)).status())
                .isEqualTo(ProviderOperationStatus.UNKNOWN);
        assertThat(failing.status(payment(PaymentOperation.STATUS)).status())
                .isEqualTo(ProviderOperationStatus.UNKNOWN);
    }

    @Test
    void paymentRejectsWrongOperationBeforeCallingProvider() {
        var provider = new RecordingPaymentProvider();
        var service = new DefaultPaymentService(provider);

        assertThatIllegalArgumentException().isThrownBy(() -> service.refund(payment(PaymentOperation.CREATE)));
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void payoutDelegatesAndConvertsProviderFailureToUnknown() {
        var provider = new PayoutProviderPort() {
            @Override public String providerName() { return "TEST"; }
            @Override public ProviderOperationResult submit(PayoutRequest request) {
                return new ProviderOperationResult(ProviderOperationStatus.PROCESSING, "payout-1", null, true);
            }
            @Override public ProviderOperationResult status(PayoutRequest request) {
                throw new IllegalStateException("unavailable");
            }
        };
        var service = new DefaultPayoutService(provider);
        var request = new PayoutRequest(ID, "idem-1", "merchant-1", EntityType.SHIPPER, 9L,
                MoneyAmount.vnd(BigDecimal.ONE), "ledger-1", null);

        assertThat(service.submit(request).status()).isEqualTo(ProviderOperationStatus.PROCESSING);
        assertThat(service.status(request).status()).isEqualTo(ProviderOperationStatus.UNKNOWN);
    }

    @Test
    void validatesDependenciesAndRequests() {
        assertThatThrownBy(() -> new DefaultPaymentService(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultPayoutService(null)).isInstanceOf(NullPointerException.class);
        var paymentService = new DefaultPaymentService(new RecordingPaymentProvider());
        assertThatThrownBy(() -> paymentService.create(null)).isInstanceOf(NullPointerException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> paymentService.refund(payment(PaymentOperation.CREATE)));
        assertThatIllegalArgumentException().isThrownBy(() -> paymentService.status(payment(PaymentOperation.CREATE)));
        var payoutService = new DefaultPayoutService(new PayoutProviderPort() {
            @Override public String providerName() { return "TEST"; }
            @Override public ProviderOperationResult submit(PayoutRequest request) { return null; }
            @Override public ProviderOperationResult status(PayoutRequest request) { return null; }
        });
        assertThatThrownBy(() -> payoutService.submit(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> payoutService.status(null)).isInstanceOf(NullPointerException.class);
        assertThat(payoutService.submit(new PayoutRequest(ID, "idem", "merchant", EntityType.SHIPPER, 1L,
                MoneyAmount.vnd(BigDecimal.ONE), "ledger", null)).status())
                .isEqualTo(ProviderOperationStatus.UNKNOWN);
    }

    @Test
    void unifiedFacadeDelegatesPaymentAndPayoutOperations() {
        var payments = new RecordingPaymentProvider();
        var payouts = new PayoutProviderPort() {
            @Override public String providerName() { return "TEST"; }
            @Override public ProviderOperationResult submit(PayoutRequest request) {
                return new ProviderOperationResult(ProviderOperationStatus.PROCESSING, "payout", null, true);
            }
            @Override public ProviderOperationResult status(PayoutRequest request) {
                return new ProviderOperationResult(ProviderOperationStatus.SUCCEEDED, "payout", null, false);
            }
        };
        var service = new DefaultSettlementService(payments, payouts);
        var request = new PayoutRequest(ID, "idem", "merchant", EntityType.SHIPPER, 1L,
                MoneyAmount.vnd(BigDecimal.ONE), "ledger", null);
        assertThat(service.create(payment(PaymentOperation.CREATE))).isNotNull();
        assertThat(service.refund(payment(PaymentOperation.REFUND))).isNotNull();
        assertThat(service.status(payment(PaymentOperation.STATUS))).isNotNull();
        assertThat(service.submit(request).status()).isEqualTo(ProviderOperationStatus.PROCESSING);
        assertThat(service.status(request).status()).isEqualTo(ProviderOperationStatus.SUCCEEDED);
    }

    private static PaymentOperationRequest payment(PaymentOperation operation) {
        return new PaymentOperationRequest(ID, "idem-1", "merchant-1", 7L,
                MoneyAmount.vnd(BigDecimal.TEN), "ONLINE", operation,
                operation == PaymentOperation.REFUND ? "provider-1" : null, null);
    }

    private static class RecordingPaymentProvider implements PaymentProviderPort {
        final java.util.List<String> calls = new java.util.ArrayList<>();
        @Override public String providerName() { return "TEST"; }
        @Override public ProviderOperationResult create(PaymentOperationRequest request) {
            calls.add("create");
            return result();
        }
        @Override public ProviderOperationResult refund(PaymentOperationRequest request) {
            calls.add("refund");
            return result();
        }
        @Override public ProviderOperationResult status(PaymentOperationRequest request) {
            calls.add("status");
            return result();
        }
        private ProviderOperationResult result() {
            return new ProviderOperationResult(ProviderOperationStatus.SUCCEEDED, "pay-1", null, false);
        }
    }
}
