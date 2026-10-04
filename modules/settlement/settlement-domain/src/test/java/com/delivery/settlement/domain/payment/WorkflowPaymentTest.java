package com.delivery.settlement.domain.payment;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowPaymentTest {
    @Test void callbackAmountPreservesLegacyTruncationOptionalAmountAndOverflow() {
        WorkflowPayment payment = new WorkflowPayment();
        payment.setAmount(new BigDecimal("100.99"));
        assertTrue(payment.matchesAmount(null));
        assertTrue(payment.matchesAmount(10000L));
        assertFalse(payment.matchesAmount(10099L));
        payment.setAmount(new BigDecimal(Long.MAX_VALUE));
        assertTrue(payment.matchesAmount(Long.MAX_VALUE * 100L));
    }
    @Test void onlyPendingCanTransitionAndOnlyFakeCanUseFakeConfirmation() {
        WorkflowPayment payment = new WorkflowPayment();
        payment.setStatus("PENDING"); assertTrue(payment.isPending());
        for (String terminal : new String[]{"SUCCESS", "FAILED", "EXPIRED"}) {
            payment.setStatus(terminal); assertFalse(payment.isPending());
        }
        payment.setProvider("fake"); payment.requireFake();
        payment.setProvider("VNPAY"); assertThrows(IllegalArgumentException.class, payment::requireFake);
        payment.setPurpose("DEPOSIT_TOPUP"); assertTrue(payment.isTopUp());
        payment.setPurpose("ORDER_PAYMENT"); assertFalse(payment.isTopUp());
    }
    @Test void legacyEnumDefaultsAndSupportedValuesRemainExact() {
        assertEquals("SHIPPER", WorkflowPayment.entityType(null));
        assertEquals("SHIPPER", WorkflowPayment.entityType("unknown"));
        for (String value : new String[]{"restaurant", "shipper", "system"})
            assertEquals(value.toUpperCase(), WorkflowPayment.entityType(value));
        assertEquals("DEPOSIT_TOPUP", WorkflowPayment.purpose(null));
        assertEquals("DEPOSIT_TOPUP", WorkflowPayment.purpose("unknown"));
        for (String value : new String[]{"deposit_topup", "order_payment", "withdrawal"})
            assertEquals(value.toUpperCase(), WorkflowPayment.purpose(value));
    }
}
