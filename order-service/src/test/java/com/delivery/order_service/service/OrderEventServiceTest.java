package com.delivery.order_service.service;

import com.delivery.order_service.dto.event.DeliveryStatusUpdatedEvent;
import com.delivery.order_service.dto.event.PaymentEvent;
import com.delivery.order_service.dto.event.RestaurantEvent;
import com.delivery.order_service.dto.event.ShipperEvent;
import com.delivery.order_service.entity.Order;
import com.delivery.order_service.repository.OrderRepository;
import com.delivery.order_service.service.impl.OrderEventServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ✅ Order Event Service Test theo AI Coding Instructions
 */
@ExtendWith(MockitoExtension.class)
class OrderEventServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderEventService orderEventService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        // ✅ Constructor Injection (MANDATORY)
        orderEventService = new OrderEventServiceImpl(orderRepository);

        // Setup test order
        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setUserId(123L);
        testOrder.setRestaurantId(456L);
        testOrder.setStatus("PENDING");
        testOrder.setTotalPrice(new BigDecimal("150000"));
        testOrder.setNotes("");
        testOrder.setCreatedAt(LocalDateTime.now());
        testOrder.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testHandleDeliveryStatusUpdate_Success() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent();
        event.setOrderId(1L);
        event.setStatus("DELIVERED");
        event.setNotes("Delivered successfully");
        event.setUpdatedAt(LocalDateTime.now());

        // When
        orderEventService.handleDeliveryStatusUpdate(event);

        // Then
        verify(orderRepository).findById(1L);
        verify(orderRepository).save(any(Order.class));
        assert testOrder.getStatus().equals("DELIVERED");
    }

    @Test
    void testHandlePaymentCompleted_Success() {
        // Given
        testOrder.setStatus("PENDING_PAYMENT");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        PaymentEvent event = new PaymentEvent();
        event.setOrderId(1L);
        event.setStatus("COMPLETED");
        event.setAmount(150000.0);
        event.setProcessedAt(LocalDateTime.now());

        // When
        orderEventService.handlePaymentCompleted(event);

        // Then
        verify(orderRepository).findById(1L);
        verify(orderRepository).save(any(Order.class));
        assert testOrder.getStatus().equals("CONFIRMED");
    }

    @Test
    void testHandleRestaurantConfirmed_Success() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        RestaurantEvent event = new RestaurantEvent();
        event.setOrderId(1L);
        event.setStatus("CONFIRMED");
        event.setEstimatedPrepTime(25);
        event.setNotes("Order confirmed, will be ready in 25 minutes");
        event.setProcessedAt(LocalDateTime.now());

        // When
        orderEventService.handleRestaurantConfirmed(event);

        // Then
        verify(orderRepository).findById(1L);
        verify(orderRepository).save(any(Order.class));
        assert testOrder.getStatus().equals("CONFIRMED_BY_RESTAURANT");
    }

    @Test
    void testHandleShipperAccepted_Success() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        ShipperEvent event = new ShipperEvent();
        event.setOrderId(1L);
        event.setShipperId(789L);
        event.setAction("ACCEPTED");
        event.setEstimatedPickupTime(15.0);
        event.setNotes("Shipper on the way");
        event.setResponseTime(LocalDateTime.now());

        // When
        orderEventService.handleShipperAccepted(event);

        // Then
        verify(orderRepository).findById(1L);
        verify(orderRepository).save(any(Order.class));
        assert testOrder.getStatus().equals("ASSIGNED_TO_SHIPPER");
        assert testOrder.getShipperId().equals(789L);
    }

    @Test
    void testHandlePaymentFailed_Success() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        PaymentEvent event = new PaymentEvent();
        event.setOrderId(1L);
        event.setStatus("FAILED");
        event.setFailureReason("Insufficient funds");
        event.setProcessedAt(LocalDateTime.now());

        // When
        orderEventService.handlePaymentFailed(event);

        // Then
        verify(orderRepository).findById(1L);
        verify(orderRepository).save(any(Order.class));
        assert testOrder.getStatus().equals("PAYMENT_FAILED");
    }
}
