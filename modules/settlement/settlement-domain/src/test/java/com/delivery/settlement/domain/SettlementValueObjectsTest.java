package com.delivery.settlement.domain;

import com.delivery.settlement.domain.payment.MoneyAmount;
import com.delivery.settlement.domain.payment.PaymentOperation;
import com.delivery.settlement.domain.payment.PaymentOperationRequest;
import com.delivery.settlement.domain.payment.PayoutRequest;
import com.delivery.settlement.domain.payment.ProviderOperationResult;
import com.delivery.settlement.domain.payment.ProviderOperationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementValueObjectsTest {

    @Test
    void canonicalizesMoneyWithoutRounding() {
        MoneyAmount amount = new MoneyAmount(new BigDecimal("12"), " vnd ");

        assertEquals(0, amount.amount().compareTo(new BigDecimal("12.00")));
        assertEquals("VND", amount.currency());
        assertTrue(amount.isPositive());
    }

    @Test
    void paymentRequestCopiesMetadataAndRequiresOnlineRefundReference() {
        PaymentOperationRequest request = new PaymentOperationRequest(
                UUID.randomUUID(), "idempotency", "order-1", 1L,
                MoneyAmount.vnd(new BigDecimal("10.00")), "ONLINE",
                PaymentOperation.CREATE, null, Map.of("source", "test"));

        assertEquals("ONLINE", request.paymentMethod());
        assertEquals("test", request.metadata().get("source"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new PaymentOperationRequest(
                UUID.randomUUID(), "idempotency", "order-1", 1L,
                MoneyAmount.vnd(BigDecimal.ONE), "ONLINE", PaymentOperation.REFUND,
                null, Map.of())).getMessage().contains("originalProviderReference"));
    }

    @Test
    void payoutRejectsSystemBeneficiaryAndSuccessfulResultNeedsReference() {
        assertThrows(IllegalArgumentException.class, () -> new PayoutRequest(
                UUID.randomUUID(), "idempotency", "settlement-1", EntityType.SYSTEM,
                1L, MoneyAmount.vnd(BigDecimal.ONE), "ledger-1", Map.of()));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ProviderOperationResult(
                ProviderOperationStatus.SUCCEEDED, null, null, false))
                .getMessage().contains("providerReference"));
    }

    @Test
    void coversMoneyAndRequestValidationBoundaries() {
        assertThrows(IllegalArgumentException.class, () -> new MoneyAmount(null, "VND"));
        assertThrows(IllegalArgumentException.class, () -> new MoneyAmount(new BigDecimal("-1"), "VND"));
        assertThrows(IllegalArgumentException.class, () -> new MoneyAmount(new BigDecimal("1.001"), "VND"));
        assertThrows(IllegalArgumentException.class, () -> new MoneyAmount(BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> new MoneyAmount(BigDecimal.ONE, "US"));
        assertEquals("VND", MoneyAmount.vnd(BigDecimal.ZERO).currency());

        UUID id = UUID.randomUUID();
        MoneyAmount amount = MoneyAmount.vnd(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> new PaymentOperationRequest(
                null, "key", "merchant", 1L, amount, "ONLINE", PaymentOperation.CREATE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PaymentOperationRequest(
                id, " ", "merchant", 1L, amount, "ONLINE", PaymentOperation.CREATE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PaymentOperationRequest(
                id, "key", " ", 1L, amount, "ONLINE", PaymentOperation.CREATE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PaymentOperationRequest(
                id, "key", "merchant", 0L, amount, "ONLINE", PaymentOperation.CREATE, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PaymentOperationRequest(
                id, "key", "merchant", 1L, amount, "CASH", PaymentOperation.CREATE, null, null));
        assertThrows(NullPointerException.class, () -> new PaymentOperationRequest(
                id, "key", "merchant", 1L, null, "ONLINE", PaymentOperation.CREATE, null, null));
        assertThrows(NullPointerException.class, () -> new PaymentOperationRequest(
                id, "key", "merchant", 1L, amount, "ONLINE", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new PaymentOperationRequest(
                id, "key", "merchant", 1L, amount, "ONLINE", PaymentOperation.REFUND, " ", null));
        assertEquals("ONLINE", new PaymentOperationRequest(id, "key", "merchant", 1L, amount,
                "online", PaymentOperation.CREATE, null, null).paymentMethod());
    }

    @Test
    void coversPayoutAndProviderResultBranches() {
        UUID id = UUID.randomUUID();
        MoneyAmount amount = MoneyAmount.vnd(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> new PayoutRequest(
                null, "key", "merchant", EntityType.SHIPPER, 1L, amount, "ledger", null));
        assertThrows(IllegalArgumentException.class, () -> new PayoutRequest(
                id, " ", "merchant", EntityType.SHIPPER, 1L, amount, "ledger", null));
        assertThrows(IllegalArgumentException.class, () -> new PayoutRequest(
                id, "key", " ", EntityType.SHIPPER, 1L, amount, "ledger", null));
        assertThrows(IllegalArgumentException.class, () -> new PayoutRequest(
                id, "key", "merchant", null, 1L, amount, "ledger", null));
        assertThrows(IllegalArgumentException.class, () -> new PayoutRequest(
                id, "key", "merchant", EntityType.SHIPPER, 0L, amount, "ledger", null));
        assertThrows(IllegalArgumentException.class, () -> new PayoutRequest(
                id, "key", "merchant", EntityType.SHIPPER, 1L, MoneyAmount.vnd(BigDecimal.ZERO), "ledger", null));
        assertThrows(IllegalArgumentException.class, () -> new PayoutRequest(
                id, "key", "merchant", EntityType.SHIPPER, 1L, amount, " ", null));

        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationResult(null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationResult(
                ProviderOperationStatus.FAILED, " ", null, false));
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationResult(
                ProviderOperationStatus.FAILED, null, "x".repeat(2001), false));
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationResult(
                ProviderOperationStatus.PARTIAL, null, null, false));
        assertEquals("provider-ref", new ProviderOperationResult(
                ProviderOperationStatus.SUCCEEDED, "provider-ref", "ok", false).providerReference());
        assertEquals("partial-ref", new ProviderOperationResult(
                ProviderOperationStatus.PARTIAL, "partial-ref", null, false).providerReference());
        assertEquals(ProviderOperationStatus.UNKNOWN, ProviderOperationResult.unknown("timeout").status());
        assertEquals(ProviderOperationStatus.FAILED, ProviderOperationResult.failed("error", true).status());
    }
}
