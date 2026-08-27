package com.delivery.settlement_service.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.settlement_service.service.CustomerPaymentBoundaryService;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomerPaymentControllerTest {

    private final CustomerPaymentController controller =
            new CustomerPaymentController(new CustomerPaymentBoundaryService());

    @Test
    void createReturnsExplicitConflictInsteadOfAcceptingAnUnownedOrderPayment() {
        var actor = new AuthenticatedActor(7L, 8L, "customer@example.test", Set.of("USER"));

        var response = controller.create(actor, new CustomerPaymentController.CustomerPaymentCreateRequest(99L));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("CUSTOMER_ORDER_PAYMENT_UNSUPPORTED");
    }

    @Test
    void referenceLookupRejectsNonUserActorsBeforeLookingUpPaymentData() {
        var actor = new AuthenticatedActor(7L, 8L, "shipper@example.test", Set.of("SHIPPER"));

        var response = controller.getByReference(actor, "PAY-99");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Only USER can access this endpoint");
    }

    @Test
    void createStaysFailClosedIfAFutureBoundaryReturnsWithoutAnOwnershipDecision() {
        var permissiveBoundary = new CustomerPaymentBoundaryService() {
            @Override
            public void createOrderPayment(AuthenticatedActor actor, Long orderId) {
                // Simulates an incomplete future implementation: it must not open a 2xx path.
            }
        };
        var permissiveController = new CustomerPaymentController(permissiveBoundary);
        var actor = new AuthenticatedActor(7L, 8L, "customer@example.test", Set.of("USER"));

        var response = permissiveController.create(
                actor, new CustomerPaymentController.CustomerPaymentCreateRequest(99L));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("CUSTOMER_ORDER_PAYMENT_UNSUPPORTED");
    }
}
