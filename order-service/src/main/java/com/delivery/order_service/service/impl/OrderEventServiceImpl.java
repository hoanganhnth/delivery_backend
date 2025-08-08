package com.delivery.order_service.service.impl;

import com.delivery.order_service.dto.event.DeliveryStatusUpdatedEvent;
import com.delivery.order_service.dto.event.PaymentEvent;
import com.delivery.order_service.dto.event.RestaurantEvent;
import com.delivery.order_service.dto.event.ShipperEvent;
import com.delivery.order_service.entity.Order;
import com.delivery.order_service.repository.OrderRepository;
import com.delivery.order_service.service.OrderEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * ✅ Order Event Service Implementation theo AI Coding Instructions
 */
@Slf4j
@Service
public class OrderEventServiceImpl implements OrderEventService {

    private final OrderRepository orderRepository;

    // ✅ Constructor Injection (MANDATORY)
    public OrderEventServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public void handleDeliveryStatusUpdate(DeliveryStatusUpdatedEvent event) {
        log.info("🚚 Processing delivery status update: orderId={}, deliveryId={}, status={}", 
                event.getOrderId(), event.getDeliveryId(), event.getStatus());

        // ✅ Sử dụng orderId từ event để tìm và cập nhật order
        Order order = findOrderById(event.getOrderId());

        // Map delivery status to order status
        String newOrderStatus = mapDeliveryStatusToOrderStatus(event.getStatus());
        
        if (newOrderStatus != null && !newOrderStatus.equals(order.getStatus())) {
            String previousStatus = order.getStatus();
            order.setStatus(newOrderStatus);
            order.setUpdatedAt(LocalDateTime.now());

            // Add notes if available
            if (event.getNotes() != null) {
                order.setNotes(order.getNotes() + "\n" + event.getNotes());
            }

            orderRepository.save(order);
            
            log.info("✅ Updated order {} status: {} -> {}", 
                    order.getId(), previousStatus, newOrderStatus);
        }
    }

    @Override
    @Transactional
    public void handlePaymentCompleted(PaymentEvent event) {
        log.info("💳 Processing payment completed: orderId={}, amount={}", 
                event.getOrderId(), event.getAmount());

        Order order = findOrderById(event.getOrderId());

        if ("PENDING_PAYMENT".equals(order.getStatus())) {
            order.setStatus("CONFIRMED");
            order.setUpdatedAt(LocalDateTime.now());
            order.setNotes(order.getNotes() + "\nPayment completed: " + event.getAmount());

            orderRepository.save(order);
            
            log.info("✅ Order {} marked as CONFIRMED after payment", order.getId());
        }
    }

    @Override
    @Transactional
    public void handlePaymentFailed(PaymentEvent event) {
        log.info("💳 Processing payment failed: orderId={}, reason={}", 
                event.getOrderId(), event.getFailureReason());

        Order order = findOrderById(event.getOrderId());

        order.setStatus("PAYMENT_FAILED");
        order.setUpdatedAt(LocalDateTime.now());
        if (event.getFailureReason() != null) {
            order.setNotes(order.getNotes() + "\nPayment failed: " + event.getFailureReason());
        }

        orderRepository.save(order);
        
        log.info("✅ Order {} marked as PAYMENT_FAILED", order.getId());
    }

    @Override
    @Transactional
    public void handleRestaurantConfirmed(RestaurantEvent event) {
        log.info("🏪 Processing restaurant confirmation: orderId={}, estimatedTime={}", 
                event.getOrderId(), event.getEstimatedPrepTime());

        Order order = findOrderById(event.getOrderId());

        order.setStatus("CONFIRMED_BY_RESTAURANT");
        order.setUpdatedAt(LocalDateTime.now());

        if (event.getNotes() != null) {
            order.setNotes(order.getNotes() + "\nRestaurant confirmed: " + event.getNotes());
        }

        orderRepository.save(order);
        
        log.info("✅ Order {} confirmed by restaurant", order.getId());
    }

    @Override
    @Transactional
    public void handleRestaurantRejected(RestaurantEvent event) {
        log.info("🏪 Processing restaurant rejection: orderId={}, reason={}", 
                event.getOrderId(), event.getRejectionReason());

        Order order = findOrderById(event.getOrderId());

        order.setStatus("REJECTED_BY_RESTAURANT");
        order.setUpdatedAt(LocalDateTime.now());
        
        if (event.getRejectionReason() != null) {
            order.setNotes(order.getNotes() + "\nRejected by restaurant: " + event.getRejectionReason());
        }

        orderRepository.save(order);
        
        log.info("✅ Order {} rejected by restaurant", order.getId());
    }

    @Override
    @Transactional
    public void handleShipperAccepted(ShipperEvent event) {
        log.info("🚚 Processing shipper acceptance: orderId={}, shipperId={}", 
                event.getOrderId(), event.getShipperId());

        Order order = findOrderById(event.getOrderId());

        order.setStatus("ASSIGNED_TO_SHIPPER");
        order.setShipperId(event.getShipperId());
        order.setUpdatedAt(LocalDateTime.now());

        if (event.getNotes() != null) {
            order.setNotes(order.getNotes() + "\nShipper accepted: " + event.getNotes());
        }

        orderRepository.save(order);
        
        log.info("✅ Order {} assigned to shipper {}", order.getId(), event.getShipperId());
    }

    @Override
    @Transactional
    public void handleShipperRejected(ShipperEvent event) {
        log.info("🚚 Processing shipper rejection: orderId={}, reason={}", 
                event.getOrderId(), event.getRejectReason());

        Order order = findOrderById(event.getOrderId());

        // Keep order status as CONFIRMED to allow reassignment to another shipper
        order.setUpdatedAt(LocalDateTime.now());

        if (event.getRejectReason() != null) {
            order.setNotes(order.getNotes() + "\nShipper rejected: " + event.getRejectReason());
        }

        orderRepository.save(order);
        
        log.info("✅ Order {} rejected by shipper, available for reassignment", order.getId());
    }

    /**
     * ✅ Helper method to find order by ID
     */
    private Order findOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));
    }

    /**
     * ✅ Map delivery status to order status
     */
    private String mapDeliveryStatusToOrderStatus(String deliveryStatus) {
        switch (deliveryStatus) {
            case "ASSIGNED":
                return "ASSIGNED_TO_SHIPPER";
            case "IN_PROGRESS":
            case "PICKED_UP":
                return "IN_DELIVERY";
            case "DELIVERED":
                return "DELIVERED";
            case "CANCELLED":
                return "CANCELLED";
            default:
                log.warn("⚠️ Unknown delivery status: {}", deliveryStatus);
                return null;
        }
    }
}
