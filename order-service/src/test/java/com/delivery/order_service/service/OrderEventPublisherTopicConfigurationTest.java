package com.delivery.order_service.service;

import com.delivery.order_service.dto.event.OrderCancelledEvent;
import com.delivery.order_service.dto.event.OrderCreatedEvent;
import com.delivery.order_service.entity.Order;
import com.delivery.order_service.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

class OrderEventPublisherTopicConfigurationTest {

    @Test
    void outputTopicsCanBeOverriddenWithoutChangingCanonicalEventTypes() {
        OrderOutboxService outboxService = mock(OrderOutboxService.class);
        OrderEventPublisher publisher = new OrderEventPublisher(outboxService);
        ReflectionTestUtils.setField(publisher, "orderCreatedTopic", "b8.order.created");
        ReflectionTestUtils.setField(publisher, "orderCancelledTopic", "b8.order.cancelled");
        Order order = order();

        publisher.publishOrderCreatedEvent(order);
        publisher.publishOrderCancelledEvent(order, "PENDING", 30L);

        verify(outboxService).enqueue(eq("ORDER_CREATED"), eq("970001"),
                eq("b8.order.created"), eq("970001"), any());
        verify(outboxService).enqueue(eq("ORDER_CANCELLED"), eq("970001"),
                eq("b8.order.cancelled"), eq("970001"), any());
    }

    @Test
    void createdAndCancelledPayloadsCarryTheirStableReservationIdentity() {
        OrderOutboxService outboxService = mock(OrderOutboxService.class);
        OrderEventPublisher publisher = new OrderEventPublisher(outboxService);
        ReflectionTestUtils.setField(publisher, "orderCreatedTopic", "order.created");
        ReflectionTestUtils.setField(publisher, "orderCancelledTopic", "order.cancelled");
        Order order = order();
        UUID voucherId = UUID.randomUUID();
        UUID flashId = UUID.randomUUID();
        order.setVoucherReservationId(voucherId);

        publisher.publishOrderCreatedEvent(order);
        order.setVoucherReservationId(null);
        order.setFlashSaleReservationId(flashId);
        publisher.publishOrderCancelledEvent(order, "PENDING", 30L);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).enqueue(eq("ORDER_CREATED"), eq("970001"),
                eq("order.created"), eq("970001"), payload.capture());
        verify(outboxService).enqueue(eq("ORDER_CANCELLED"), eq("970001"),
                eq("order.cancelled"), eq("970001"), payload.capture());
        assertThat(payload.getAllValues().get(0)).isInstanceOfSatisfying(OrderCreatedEvent.class, event -> {
            assertThat(event.getVoucherReservationId()).isEqualTo(voucherId);
            assertThat(event.getFlashSaleReservationId()).isNull();
        });
        assertThat(payload.getAllValues().get(1)).isInstanceOfSatisfying(OrderCancelledEvent.class, event -> {
            assertThat(event.getVoucherReservationId()).isNull();
            assertThat(event.getFlashSaleReservationId()).isEqualTo(flashId);
            assertThat(event.getSubtotalPrice()).isEqualByComparingTo("100000");
            assertThat(event.getDiscountAmount()).isEqualByComparingTo("5000");
            assertThat(event.getShippingFee()).isEqualByComparingTo("25000");
            assertThat(event.getTotalPrice()).isEqualByComparingTo("120000");
            assertThat(event.getPaymentMethod()).isEqualTo("COD");
        });
    }

    private Order order() {
        Order order = new Order();
        order.setId(970001L);
        order.setUserId(9700L);
        order.setCreatorId(9700L);
        order.setRestaurantId(97L);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod("COD");
        order.setSubtotalPrice(new java.math.BigDecimal("100000"));
        order.setDiscountAmount(new java.math.BigDecimal("5000"));
        order.setShippingFee(new java.math.BigDecimal("25000"));
        order.setTotalPrice(new java.math.BigDecimal("120000"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(order.getCreatedAt());
        return order;
    }
}
