package com.delivery.order_service.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

import com.delivery.order_service.dto.request.CheckoutPreviewRequest;
import com.delivery.order_service.exception.AccessDeniedException;
import com.delivery.order_service.service.CheckoutPreviewService;
import com.delivery.order_service.service.OrderService;

class OrderControllerAuthorizationTest {

    private final OrderService orderService = mock(OrderService.class);
    private final CheckoutPreviewService previewService = mock(CheckoutPreviewService.class);
    private final OrderController controller = new OrderController(orderService, previewService);

    @Test
    void checkoutPreviewRejectsNonCustomerBeforeCallingDependencies() {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest();

        assertThatThrownBy(() -> controller.checkoutPreview(request, 7L, "SHOP_OWNER"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(orderService, previewService);
    }

    @Test
    void selfOrderListsRejectWrongActorsBeforeQuery() {
        assertThatThrownBy(() -> controller.getMyOrders(7L, "SHIPPER", 0, 10))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.getMyRestaurantOrders(7L, "USER", 0, 10))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderService, previewService);
    }
}
