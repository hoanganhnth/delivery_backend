package com.delivery.order_service.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.order_service.dto.request.CheckoutPreviewRequest;
import com.delivery.order_service.exception.AccessDeniedException;
import com.delivery.order_service.service.CheckoutPreviewService;
import com.delivery.order_service.service.OrderService;

import java.util.Set;

class OrderControllerAuthorizationTest {

    private final OrderService orderService = mock(OrderService.class);
    private final CheckoutPreviewService previewService = mock(CheckoutPreviewService.class);
    private final OrderController controller = new OrderController(orderService, previewService);

    @Test
    void checkoutPreviewRejectsNonCustomerBeforeCallingDependencies() {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest();
        AuthenticatedActor shopActor = new AuthenticatedActor(7L, "shop@example.com", Set.of("SHOP_OWNER"));

        assertThatThrownBy(() -> controller.checkoutPreview(request, shopActor))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(orderService, previewService);
    }

    @Test
    void selfOrderListsRejectWrongActorsBeforeQuery() {
        AuthenticatedActor shipperActor = new AuthenticatedActor(7L, "shipper@example.com", Set.of("SHIPPER"));
        AuthenticatedActor userActor = new AuthenticatedActor(7L, "user@example.com", Set.of("USER"));

        assertThatThrownBy(() -> controller.getMyOrders(shipperActor, 0, 10))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.getMyRestaurantOrders(userActor, 0, 10))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderService, previewService);
    }

    @Test
    void orderManagementListsRejectNonAdminBeforeQuery() {
        AuthenticatedActor ownerActor = new AuthenticatedActor(7L, "shop@example.com", Set.of("SHOP_OWNER"));

        assertThatThrownBy(() -> controller.getAllOrders(ownerActor, 0, 10))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.getOrdersByStatus("PENDING", ownerActor, 0, 10))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderService, previewService);
    }
}
