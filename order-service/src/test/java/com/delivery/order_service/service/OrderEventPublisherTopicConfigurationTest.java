package com.delivery.order_service.service;

import com.delivery.order_service.entity.Order;
import com.delivery.order_service.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    private Order order() {
        Order order = new Order();
        order.setId(970001L);
        order.setUserId(9700L);
        order.setCreatorId(9700L);
        order.setRestaurantId(97L);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod("COD");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(order.getCreatedAt());
        return order;
    }
}
