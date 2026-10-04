package com.delivery.settlement.domain.payment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerPaymentPolicyTest {
    private final CustomerPaymentPolicy.Identity customer = new CustomerPaymentPolicy.Identity(true, 91L, 42L);

    @Test void rejectsMissingOrNonCustomerIdentityBeforeInputValidation() {
        for (var identity : new CustomerPaymentPolicy.Identity[]{null,
                new CustomerPaymentPolicy.Identity(false, 91L, 42L)}) {
            assertEquals("Only USER can access this endpoint", assertThrows(
                    CustomerPaymentPolicy.AccessDenied.class, () -> CustomerPaymentPolicy.create(identity, null)).getMessage());
            assertThrows(CustomerPaymentPolicy.AccessDenied.class, () -> CustomerPaymentPolicy.byReference(identity, null));
        }
    }

    @Test void requiresBothPositiveIdentitiesBeforeInputValidation() {
        for (var identity : new CustomerPaymentPolicy.Identity[]{
                new CustomerPaymentPolicy.Identity(true, null, 42L),
                new CustomerPaymentPolicy.Identity(true, 0L, 42L),
                new CustomerPaymentPolicy.Identity(true, -1L, 42L),
                new CustomerPaymentPolicy.Identity(true, 91L, null),
                new CustomerPaymentPolicy.Identity(true, 91L, 0L),
                new CustomerPaymentPolicy.Identity(true, 91L, -1L)}) {
            assertEquals("Authenticated user identity is required", assertThrows(
                    CustomerPaymentPolicy.AccessDenied.class, () -> CustomerPaymentPolicy.create(identity, null)).getMessage());
        }
    }

    @Test void validatesOrderAndFailsClosedForEveryValidOrder() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertEquals("orderId must be positive", assertThrows(IllegalArgumentException.class,
                    () -> CustomerPaymentPolicy.create(customer, id)).getMessage());
        }
        assertEquals("CUSTOMER_ORDER_PAYMENT_UNSUPPORTED", assertThrows(CustomerPaymentPolicy.Unsupported.class,
                () -> CustomerPaymentPolicy.create(customer, 77L)).getMessage());
    }

    @Test void validatesReferenceWithoutLookingUpAnyPayment() {
        for (String ref : new String[]{null, "PAY-", "pay-123", "PAY-a/b", "PAY-" + "a".repeat(60)}) {
            assertEquals("paymentRef is invalid", assertThrows(IllegalArgumentException.class,
                    () -> CustomerPaymentPolicy.byReference(customer, ref)).getMessage());
        }
        for (String ref : new String[]{"PAY-123", "PAY-" + "a".repeat(59), "PAY-a-Z9"}) {
            assertEquals("CUSTOMER_PAYMENT_OWNERSHIP_UNSUPPORTED", assertThrows(CustomerPaymentPolicy.Unsupported.class,
                    () -> CustomerPaymentPolicy.byReference(customer, ref)).getMessage());
        }
    }
}
