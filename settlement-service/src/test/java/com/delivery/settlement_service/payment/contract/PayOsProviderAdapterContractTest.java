package com.delivery.settlement_service.payment.contract;

import com.delivery.settlement_service.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayOsProviderAdapterContractTest {

    @Test
    void delegatesPaymentRefundAndPayoutWithoutChangingStableIdentity() {
        AtomicReference<PaymentOperationRequest> paymentSeen = new AtomicReference<>();
        AtomicReference<PaymentOperationRequest> refundSeen = new AtomicReference<>();
        AtomicReference<PayoutRequest> payoutSeen = new AtomicReference<>();
        UUID operationId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        PayOsClient client = new PayOsClient() {
            @Override
            public ProviderOperationResult createPayment(PaymentOperationRequest request) {
                paymentSeen.set(request);
                return new ProviderOperationResult(ProviderOperationStatus.PROCESSING,
                        "pay-ref", "accepted", true);
            }

            @Override
            public ProviderOperationResult refund(PaymentOperationRequest request) {
                refundSeen.set(request);
                return new ProviderOperationResult(ProviderOperationStatus.UNKNOWN,
                        null, "provider timeout", true);
            }

            @Override
            public ProviderOperationResult status(PaymentOperationRequest request) {
                return new ProviderOperationResult(ProviderOperationStatus.SUCCEEDED,
                        "pay-ref", null, false);
            }

            @Override
            public ProviderOperationResult submitPayout(PayoutRequest request) {
                payoutSeen.set(request);
                return new ProviderOperationResult(ProviderOperationStatus.REQUESTED,
                        null, "queued", true);
            }

            @Override
            public ProviderOperationResult payoutStatus(PayoutRequest request) {
                return new ProviderOperationResult(ProviderOperationStatus.SUCCEEDED,
                        "payout-ref", null, false);
            }
        };

        PayOsProviderAdapter adapter = new PayOsProviderAdapter(client);
        PaymentOperationRequest create = new PaymentOperationRequest(operationId, "order-101:create",
                "order-101", 101L, MoneyAmount.vnd(new BigDecimal("120000")), "ONLINE",
                PaymentOperation.CREATE, null, Map.of("source", "checkout"));
        PaymentOperationRequest refund = new PaymentOperationRequest(operationId, "refund-101",
                "order-101", 101L, MoneyAmount.vnd(new BigDecimal("120000")), "ONLINE",
                PaymentOperation.REFUND, "provider-101", Map.of());
        PayoutRequest payout = new PayoutRequest(payoutId, "payout-101", "settlement-101",
                EntityType.SHIPPER, 19L, MoneyAmount.vnd(new BigDecimal("50000")),
                "ledger-101", Map.of());

        assertThat(adapter.providerName()).isEqualTo("PAYOS");
        assertThat(adapter.create(create).status()).isEqualTo(ProviderOperationStatus.PROCESSING);
        assertThat(adapter.refund(refund).status()).isEqualTo(ProviderOperationStatus.UNKNOWN);
        assertThat(adapter.submit(payout).status()).isEqualTo(ProviderOperationStatus.REQUESTED);
        assertThat(paymentSeen.get()).isSameAs(create);
        assertThat(refundSeen.get()).isSameAs(refund);
        assertThat(payoutSeen.get()).isSameAs(payout);
    }

    @Test
    void rejectsContradictoryOperationAndInvalidMoneyAtTheContractBoundary() {
        PayOsProviderAdapter adapter = new PayOsProviderAdapter(new NoopPayOsClient());
        PaymentOperationRequest status = request(PaymentOperation.STATUS);

        assertThatThrownBy(() -> adapter.create(status))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CREATE");
        assertThatThrownBy(() -> new MoneyAmount(new BigDecimal("1.001"), "VND"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PayoutRequest(UUID.randomUUID(), "payout", "settlement",
                EntityType.SYSTEM, 1L, MoneyAmount.vnd(BigDecimal.ONE), "ledger", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("beneficiary");
    }

    private PaymentOperationRequest request(PaymentOperation operation) {
        return new PaymentOperationRequest(UUID.randomUUID(), "idempotency", "order-101", 101L,
                MoneyAmount.vnd(BigDecimal.ONE), "ONLINE", operation,
                operation == PaymentOperation.REFUND ? "provider-ref" : null, Map.of());
    }

    private static final class NoopPayOsClient implements PayOsClient {
        @Override public ProviderOperationResult createPayment(PaymentOperationRequest request) {
            return ProviderOperationResult.unknown("not wired");
        }
        @Override public ProviderOperationResult refund(PaymentOperationRequest request) {
            return ProviderOperationResult.unknown("not wired");
        }
        @Override public ProviderOperationResult status(PaymentOperationRequest request) {
            return ProviderOperationResult.unknown("not wired");
        }
        @Override public ProviderOperationResult submitPayout(PayoutRequest request) {
            return ProviderOperationResult.unknown("not wired");
        }
        @Override public ProviderOperationResult payoutStatus(PayoutRequest request) {
            return ProviderOperationResult.unknown("not wired");
        }
    }
}
