package com.delivery.order_service.service;

import com.delivery.order_service.dto.internal.ValidatedOrderData;
import com.delivery.order_service.dto.request.CreateOrderRequest;
import com.delivery.order_service.dto.response.OrderResponse;
import com.delivery.order_service.entity.Order;
import com.delivery.order_service.entity.OrderItem;
import com.delivery.order_service.mapper.OrderMapper;
import com.delivery.order_service.repository.OrderItemRepository;
import com.delivery.order_service.repository.OrderRepository;
import com.delivery.order_service.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class OrderServiceCanonicalPricingTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderMapper orderMapper;
    @Mock OrderEventPublisher orderEventPublisher;
    @Mock OrderValidationService orderValidationService;
    @Mock ShippingFeeCalculationService shippingFeeCalculationService;
    @Mock com.delivery.order_service.metrics.BusinessMetrics businessMetrics;
    @Mock CheckoutReservationClient reservationClient;

    @Test
    void customerCancellationLocksOrderBeforeTransitionAndOutbox() {
        Order order = new Order();
        order.setId(101L);
        order.setUserId(21L);
        order.setStatus(com.delivery.order_service.entity.OrderStatus.PENDING);
        when(orderRepository.findByIdForUpdate(101L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.orderToOrderResponse(order)).thenReturn(new OrderResponse());
        OrderServiceImpl service = new OrderServiceImpl(
                orderRepository, orderItemRepository, orderMapper, orderEventPublisher,
                orderValidationService, shippingFeeCalculationService, businessMetrics, reservationClient);

        service.cancelOrder(101L, 21L, "USER", "changed mind");

        org.mockito.InOrder mutation = org.mockito.Mockito.inOrder(orderRepository, orderEventPublisher);
        mutation.verify(orderRepository).findByIdForUpdate(101L);
        mutation.verify(orderRepository).save(order);
        mutation.verify(orderEventPublisher).publishOrderCancelledEvent(order, "PENDING", 21L);
        org.assertj.core.api.Assertions.assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    void exactDuplicateCustomerCancellationIsIdempotentWithoutAnotherOutbox() {
        Order order = new Order();
        order.setId(101L);
        order.setUserId(21L);
        order.setStatus(com.delivery.order_service.entity.OrderStatus.PENDING);
        when(orderRepository.findByIdForUpdate(101L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.orderToOrderResponse(order)).thenReturn(new OrderResponse());
        OrderServiceImpl service = service();

        service.cancelOrder(101L, 21L, "USER", "changed mind");
        service.cancelOrder(101L, 21L, "USER", "changed mind");

        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.times(2)).findByIdForUpdate(101L);
        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.times(1)).save(order);
        org.mockito.Mockito.verify(orderEventPublisher, org.mockito.Mockito.times(1))
                .publishOrderCancelledEvent(order, "PENDING", 21L);
    }

    @Test
    void duplicateCancellationWithDifferentReasonIsRejectedWithoutAnotherOutbox() {
        Order order = new Order();
        order.setId(101L);
        order.setUserId(21L);
        order.setStatus(com.delivery.order_service.entity.OrderStatus.CANCELLED);
        order.setCancelledBy(21L);
        order.setCancelReason("changed mind");
        when(orderRepository.findByIdForUpdate(101L)).thenReturn(java.util.Optional.of(order));
        OrderServiceImpl service = service();

        assertThrows(IllegalStateException.class,
                () -> service.cancelOrder(101L, 21L, "USER", "different reason"));

        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void shippingFeeUsesServerValidatedPickupCoordinates() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(7L);
        request.setPickupLat(1.0);
        request.setPickupLng(2.0);
        request.setDeliveryLat(10.8);
        request.setDeliveryLng(106.7);

        CreateOrderRequest.OrderItemRequest itemRequest = new CreateOrderRequest.OrderItemRequest();
        itemRequest.setMenuItemId(9L);
        itemRequest.setMenuItemName("Cơm");
        itemRequest.setPrice(new BigDecimal("50000"));
        itemRequest.setQuantity(2);
        request.setItems(List.of(itemRequest));

        ValidatedOrderData validated = new ValidatedOrderData(
                11L, "Quán", "Địa chỉ", "0900000000", 10.75, 106.66,
                List.of(new ValidatedOrderData.ValidatedItemData(
                        9L, "Cơm canonical", new BigDecimal("50000"))));
        when(orderValidationService.validateCreateOrderRequest(request, 21L)).thenReturn(validated);

        Order order = new Order();
        order.setId(101L);
        order.setRestaurantId(7L);
        when(orderMapper.createOrderRequestToOrder(request)).thenReturn(order);
        when(shippingFeeCalculationService.calculateShippingFee(
                10.75, 106.66, 10.8, 106.7, new BigDecimal("100000")))
                .thenReturn(new BigDecimal("15000"));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(orderRepository.save(order)).thenReturn(order);

        OrderItem orderItem = new OrderItem();
        when(orderMapper.orderItemRequestToOrderItem(itemRequest)).thenReturn(orderItem);
        when(orderMapper.orderToOrderResponse(order)).thenReturn(new OrderResponse());

        OrderServiceImpl service = service();

        service.createOrder(request, 21L, "USER");

        verify(shippingFeeCalculationService).calculateShippingFee(
                10.75, 106.66, 10.8, 106.7, new BigDecimal("100000"));
        verify(orderEventPublisher).publishOrderCreatedEvent(order);
        assertEquals("Cơm canonical", orderItem.getMenuItemName());
        assertEquals(new BigDecimal("50000"), orderItem.getPrice());
    }

    @Test
    void voucherDiscountAndReservationIdentityAreSnapshottedIntoOrder() {
        CreateOrderRequest request = baseRequest();
        request.setVoucherIds(List.of(55L));
        ValidatedOrderData validated = validatedItem(new BigDecimal("100000"));
        when(orderValidationService.validateCreateOrderRequest(request, 21L)).thenReturn(validated);
        Order order = persistedMappedOrder(request);
        when(shippingFeeCalculationService.calculateShippingFee(
                10.75, 106.66, 10.8, 106.7, new BigDecimal("100000")))
                .thenReturn(new BigDecimal("15000"));
        when(reservationClient.reserveVoucher(any(UUID.class), eq(101L), eq(21L), eq(55L), eq(7L),
                eq(new BigDecimal("100000")), eq(new BigDecimal("15000"))))
                .thenReturn(new CheckoutReservationClient.VoucherQuote(new BigDecimal("20000")));
        when(orderMapper.orderItemRequestToOrderItem(request.getItems().get(0))).thenReturn(new OrderItem());
        when(orderMapper.orderToOrderResponse(order)).thenReturn(new OrderResponse());

        service().createOrder(request, 21L, "USER");

        org.assertj.core.api.Assertions.assertThat(order.getVoucherReservationId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(order.getSubtotalPrice()).isEqualByComparingTo("100000");
        org.assertj.core.api.Assertions.assertThat(order.getDiscountAmount()).isEqualByComparingTo("20000");
        org.assertj.core.api.Assertions.assertThat(order.getShippingFee()).isEqualByComparingTo("15000");
        org.assertj.core.api.Assertions.assertThat(order.getTotalPrice()).isEqualByComparingTo("95000");
        verify(orderEventPublisher).publishOrderCreatedEvent(order);
    }

    @Test
    void flashSalePriceFromReservationOverridesRegularMenuPriceSnapshot() {
        CreateOrderRequest request = baseRequest();
        request.getItems().get(0).setFlashSaleItemId(88L);
        ValidatedOrderData validated = validatedItem(new BigDecimal("100000"));
        when(orderValidationService.validateCreateOrderRequest(request, 21L)).thenReturn(validated);
        Order order = persistedMappedOrder(request);
        when(reservationClient.reserveFlash(any(UUID.class), eq(101L), eq(21L), eq(7L), eq(request.getItems())))
                .thenReturn(new CheckoutReservationClient.FlashQuote(Map.of(88L,
                        new CheckoutReservationClient.FlashLine(88L, 9L, 1, new BigDecimal("60000")))));
        when(shippingFeeCalculationService.calculateShippingFee(
                10.75, 106.66, 10.8, 106.7, new BigDecimal("60000")))
                .thenReturn(new BigDecimal("15000"));
        OrderItem item = new OrderItem();
        when(orderMapper.orderItemRequestToOrderItem(request.getItems().get(0))).thenReturn(item);
        when(orderMapper.orderToOrderResponse(order)).thenReturn(new OrderResponse());

        service().createOrder(request, 21L, "USER");

        org.assertj.core.api.Assertions.assertThat(order.getFlashSaleReservationId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(item.getPrice()).isEqualByComparingTo("60000");
        org.assertj.core.api.Assertions.assertThat(order.getSubtotalPrice()).isEqualByComparingTo("60000");
        org.assertj.core.api.Assertions.assertThat(order.getTotalPrice()).isEqualByComparingTo("75000");
    }

    @Test
    void laterOutboxFailureReleasesSuccessfulVoucherReservation() {
        CreateOrderRequest request = baseRequest();
        request.setVoucherIds(List.of(55L));
        when(orderValidationService.validateCreateOrderRequest(request, 21L))
                .thenReturn(validatedItem(new BigDecimal("100000")));
        Order order = persistedMappedOrder(request);
        when(shippingFeeCalculationService.calculateShippingFee(
                10.75, 106.66, 10.8, 106.7, new BigDecimal("100000")))
                .thenReturn(new BigDecimal("15000"));
        when(reservationClient.reserveVoucher(any(UUID.class), eq(101L), eq(21L), eq(55L), eq(7L),
                eq(new BigDecimal("100000")), eq(new BigDecimal("15000"))))
                .thenReturn(new CheckoutReservationClient.VoucherQuote(new BigDecimal("20000")));
        when(orderMapper.orderItemRequestToOrderItem(request.getItems().get(0))).thenReturn(new OrderItem());
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(orderEventPublisher).publishOrderCreatedEvent(order);

        assertThrows(IllegalStateException.class, () -> service().createOrder(request, 21L, "USER"));

        verify(reservationClient).releaseVoucher(order.getVoucherReservationId(), 101L);
        org.mockito.Mockito.verify(reservationClient, org.mockito.Mockito.never())
                .releaseFlash(any(UUID.class), any(Long.class));
    }

    @Test
    void ambiguousVoucherReserveTimeoutAttemptsReleaseWithTheSameStableIdentity() {
        CreateOrderRequest request = baseRequest();
        request.setVoucherIds(List.of(55L));
        when(orderValidationService.validateCreateOrderRequest(request, 21L))
                .thenReturn(validatedItem(new BigDecimal("100000")));
        Order order = new Order();
        order.setId(101L); order.setRestaurantId(7L);
        when(orderMapper.createOrderRequestToOrder(request)).thenReturn(order);
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(shippingFeeCalculationService.calculateShippingFee(
                10.75, 106.66, 10.8, 106.7, new BigDecimal("100000")))
                .thenReturn(new BigDecimal("15000"));
        when(reservationClient.reserveVoucher(any(UUID.class), eq(101L), eq(21L), eq(55L), eq(7L),
                eq(new BigDecimal("100000")), eq(new BigDecimal("15000"))))
                .thenThrow(new IllegalStateException("reservation timeout"));

        assertThrows(IllegalStateException.class, () -> service().createOrder(request, 21L, "USER"));

        ArgumentCaptor<UUID> reservationId = ArgumentCaptor.forClass(UUID.class);
        verify(reservationClient).reserveVoucher(reservationId.capture(), eq(101L), eq(21L), eq(55L), eq(7L),
                eq(new BigDecimal("100000")), eq(new BigDecimal("15000")));
        verify(reservationClient).releaseVoucher(reservationId.getValue(), 101L);
    }

    private CreateOrderRequest baseRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(7L); request.setDeliveryLat(10.8); request.setDeliveryLng(106.7);
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setMenuItemId(9L); item.setQuantity(1); request.setItems(List.of(item));
        return request;
    }

    private ValidatedOrderData validatedItem(BigDecimal price) {
        return new ValidatedOrderData(11L, "Quán", "Địa chỉ", "0900000000", 10.75, 106.66,
                List.of(new ValidatedOrderData.ValidatedItemData(9L, "Cơm", price)));
    }

    private Order persistedMappedOrder(CreateOrderRequest request) {
        Order order = new Order(); order.setId(101L); order.setRestaurantId(7L);
        when(orderMapper.createOrderRequestToOrder(request)).thenReturn(order);
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(orderRepository.save(order)).thenReturn(order);
        return order;
    }

    @Test
    void nonCustomerRoleCannotCreateOrderOrReachCanonicalValidation() {
        CreateOrderRequest request = new CreateOrderRequest();

        assertThrows(com.delivery.order_service.exception.AccessDeniedException.class,
                () -> service().createOrder(request, 21L, "SHIPPER"));

        verifyNoInteractions(orderValidationService, shippingFeeCalculationService,
                orderRepository, orderItemRepository, orderEventPublisher,
                orderMapper);
    }

    @Test
    void orderDetailRequiresTheMatchingActorRoleNotOnlyMatchingUserId() {
        Order order = new Order();
        order.setId(101L);
        order.setUserId(21L);
        order.setCreatorId(31L);
        order.setShipperId(41L);
        when(orderRepository.findById(101L)).thenReturn(java.util.Optional.of(order));

        assertThrows(com.delivery.order_service.exception.AccessDeniedException.class,
                () -> service().getOrderById(101L, 21L, null));
        assertThrows(com.delivery.order_service.exception.AccessDeniedException.class,
                () -> service().getOrderById(101L, 21L, "SHIPPER"));

        verifyNoInteractions(orderMapper, orderEventPublisher);
    }

    @Test
    void customerCancellationRequiresCustomerRoleNotOnlyMatchingUserId() {
        Order order = new Order();
        order.setId(101L);
        order.setUserId(21L);
        order.setStatus(com.delivery.order_service.entity.OrderStatus.PENDING);
        when(orderRepository.findByIdForUpdate(101L)).thenReturn(java.util.Optional.of(order));

        assertThrows(com.delivery.order_service.exception.AccessDeniedException.class,
                () -> service().cancelOrder(101L, 21L, null, "changed mind"));
        assertThrows(com.delivery.order_service.exception.AccessDeniedException.class,
                () -> service().cancelOrder(101L, 21L, "SHIPPER", "changed mind"));

        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.never()).save(any());
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void shipperCannotCancelOrderThroughCustomerOrderEndpoint() {
        Order order = new Order();
        order.setId(101L);
        order.setUserId(21L);
        order.setShipperId(33L);
        order.setStatus(com.delivery.order_service.entity.OrderStatus.ASSIGNED);
        when(orderRepository.findByIdForUpdate(101L)).thenReturn(java.util.Optional.of(order));

        assertThrows(com.delivery.order_service.exception.AccessDeniedException.class,
                () -> service().cancelOrder(101L, 33L, "SHIPPER", "cannot deliver"));

        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(orderEventPublisher);
    }

    private OrderServiceImpl service() {
        return new OrderServiceImpl(
                orderRepository, orderItemRepository, orderMapper, orderEventPublisher,
                orderValidationService, shippingFeeCalculationService, businessMetrics, reservationClient);
    }
}
