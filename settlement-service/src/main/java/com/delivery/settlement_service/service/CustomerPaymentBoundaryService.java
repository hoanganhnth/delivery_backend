package com.delivery.settlement_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import org.springframework.stereotype.Service;

/**
 * Customer-facing payment boundary.
 *
 * The legacy payment table has no customer principal ownership column and the
 * order service remains COD-only. Until both are introduced together, this
 * boundary deliberately accepts no caller-controlled payer/entity data and
 * rejects create/read attempts without looking up arbitrary payment rows.
 */
@Service
public class CustomerPaymentBoundaryService {

    public void createOrderPayment(AuthenticatedActor actor, Long orderId) {
        requireCustomerIdentity(actor);
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        throw new UnsupportedCustomerPaymentException("CUSTOMER_ORDER_PAYMENT_UNSUPPORTED");
    }

    public void getByReference(AuthenticatedActor actor, String paymentRef) {
        requireCustomerIdentity(actor);
        if (paymentRef == null || !paymentRef.matches("PAY-[A-Za-z0-9-]{1,59}")) {
            throw new IllegalArgumentException("paymentRef is invalid");
        }
        throw new UnsupportedCustomerPaymentException("CUSTOMER_PAYMENT_OWNERSHIP_UNSUPPORTED");
    }

    private void requireCustomerIdentity(AuthenticatedActor actor) {
        if (actor == null || !actor.isUser()) {
            throw new CustomerPaymentAccessException("Only USER can access this endpoint");
        }
        if (actor.getPrincipalId() == null || actor.getPrincipalId() <= 0
                || actor.getLegacyUserId() == null || actor.getLegacyUserId() <= 0) {
            throw new CustomerPaymentAccessException("Authenticated user identity is required");
        }
    }

    public static final class CustomerPaymentAccessException extends RuntimeException {
        public CustomerPaymentAccessException(String message) {
            super(message);
        }
    }

    public static final class UnsupportedCustomerPaymentException extends RuntimeException {
        public UnsupportedCustomerPaymentException(String message) {
            super(message);
        }
    }
}
