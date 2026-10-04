package com.delivery.settlement_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.settlement.domain.payment.CustomerPaymentPolicy;
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
        execute(() -> CustomerPaymentPolicy.create(identity(actor), orderId));
    }

    public void getByReference(AuthenticatedActor actor, String paymentRef) {
        execute(() -> CustomerPaymentPolicy.byReference(identity(actor), paymentRef));
    }

    private CustomerPaymentPolicy.Identity identity(AuthenticatedActor actor) {
        return actor == null ? null : new CustomerPaymentPolicy.Identity(
                actor.isUser(), actor.getPrincipalId(), actor.getLegacyUserId());
    }

    private void execute(Runnable operation) {
        try {
            operation.run();
        } catch (CustomerPaymentPolicy.AccessDenied error) {
            throw new CustomerPaymentAccessException(error.getMessage());
        } catch (CustomerPaymentPolicy.Unsupported error) {
            throw new UnsupportedCustomerPaymentException(error.getMessage());
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
