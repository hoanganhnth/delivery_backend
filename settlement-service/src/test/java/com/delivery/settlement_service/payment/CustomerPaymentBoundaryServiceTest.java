package com.delivery.settlement_service.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.settlement_service.service.CustomerPaymentBoundaryService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.delivery.auth.resourceserver.security.AuthenticatedActor;

class CustomerPaymentBoundaryServiceTest {

    private final CustomerPaymentBoundaryService service = new CustomerPaymentBoundaryService();

    @Test
    void customerOrderPaymentFailsClosedUntilOrderOwnershipExists() {
        AuthenticatedActor actor = new AuthenticatedActor(91L, 42L, "customer@example.test", Set.of("USER"));

        assertThatThrownBy(() -> service.createOrderPayment(actor, 77L))
                .isInstanceOf(CustomerPaymentBoundaryService.UnsupportedCustomerPaymentException.class)
                .hasMessage("CUSTOMER_ORDER_PAYMENT_UNSUPPORTED");
    }

    @Test
    void customerPaymentStatusFailsClosedWithoutAPersistedPayerIdentity() {
        AuthenticatedActor actor = new AuthenticatedActor(91L, 42L, "customer@example.test", Set.of("USER"));

        assertThatThrownBy(() -> service.getByReference(actor, "PAY-123"))
                .isInstanceOf(CustomerPaymentBoundaryService.UnsupportedCustomerPaymentException.class)
                .hasMessage("CUSTOMER_PAYMENT_OWNERSHIP_UNSUPPORTED");
    }
}
