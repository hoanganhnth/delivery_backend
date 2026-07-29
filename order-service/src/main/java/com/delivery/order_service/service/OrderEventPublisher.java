package com.delivery.order_service.service;

import com.delivery.order_service.dto.event.OrderCreatedEvent;
import com.delivery.order_service.dto.event.OrderCancelledEvent;
import com.delivery.order_service.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ✅ Event Publisher Service cho Order Service theo Backend Instructions
 */
@Slf4j
@Service
public class OrderEventPublisher {

    private static final String ORDER_CREATED_EVENT = "ORDER_CREATED";
    private static final String ORDER_CANCELLED_EVENT = "ORDER_CANCELLED";

    private final OrderOutboxService outboxService;

    @Value("${app.kafka.topics.order-created:order.created}")
    private String orderCreatedTopic;

    @Value("${app.kafka.topics.order-cancelled:order.cancelled}")
    private String orderCancelledTopic;

    public OrderEventPublisher(OrderOutboxService outboxService) {
        this.outboxService = outboxService;
    }
    
    /**
     * Publish OrderCreatedEvent khi order được tạo thành công
     */
    public void publishOrderCreatedEvent(Order order) {
        requirePersistedOrder(order);
        OrderCreatedEvent event = mapOrderToEvent(order);
        outboxService.enqueue(
                ORDER_CREATED_EVENT,
                order.getId().toString(),
                orderCreatedTopic,
                order.getId().toString(),
                event);
        log.info("Queued OrderCreatedEvent in transactional outbox for order {}", order.getId());
    }
    
    /**
     * Publish OrderCancelledEvent khi order bị hủy
     */
    public void publishOrderCancelledEvent(Order order, String previousStatus, Long cancelledBy) {
        requirePersistedOrder(order);
        OrderCancelledEvent event = mapOrderToCancelledEvent(order, previousStatus, cancelledBy);
        outboxService.enqueue(
                ORDER_CANCELLED_EVENT,
                order.getId().toString(),
                orderCancelledTopic,
                order.getId().toString(),
                event);
        log.info("Queued OrderCancelledEvent in transactional outbox for order {}", order.getId());
    }

    private void requirePersistedOrder(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("A persisted order is required before enqueueing an event");
        }
    }
    
    /**
     * Map Order entity to OrderCancelledEvent
     */
    private OrderCancelledEvent mapOrderToCancelledEvent(Order order, String previousStatus, Long cancelledBy) {
        // Build cancel event manually to match structure
        OrderCancelledEvent cancelEvent = new OrderCancelledEvent();
        cancelEvent.setOrderId(order.getId());
        cancelEvent.setUserId(order.getUserId());
        cancelEvent.setRestaurantId(order.getRestaurantId());
        cancelEvent.setPreviousStatus(previousStatus);
        cancelEvent.setCurrentStatus("CANCELLED");
        cancelEvent.setCancelReason(order.getCancelReason());
        cancelEvent.setCancelledBy(cancelledBy);
        cancelEvent.setCancelledAt(order.getUpdatedAt() != null ? order.getUpdatedAt() : LocalDateTime.now());
        cancelEvent.setShipperId(order.getShipperId());
        cancelEvent.setHasActiveDelivery(order.getShipperId() != null);
        cancelEvent.setCreatedAt(order.getCreatedAt());
        cancelEvent.setUpdatedAt(order.getUpdatedAt());

        if (order.getItems() != null) {
            java.util.List<java.util.Map<String, Object>> items = order.getItems().stream().map(item -> {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("flashSaleItemId", item.getFlashSaleItemId());
                map.put("quantity", item.getQuantity());
                return map;
            }).toList();
            cancelEvent.setItems(items);
        }
        
        return cancelEvent;
    }
    
    /**
     * Map Order entity to OrderCreatedEvent
     */
    private OrderCreatedEvent mapOrderToEvent(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        
        // Order basic info
        event.setOrderId(order.getId());
        event.setUserId(order.getUserId());
        event.setRestaurantId(order.getRestaurantId());
        event.setStatus(order.getStatus().name());
        
        // Financial info
        event.setSubtotalPrice(order.getSubtotalPrice());
        event.setDiscountAmount(order.getDiscountAmount());
        event.setShippingFee(order.getShippingFee());
        event.setTotalPrice(order.getTotalPrice());
        event.setPaymentMethod(order.getPaymentMethod());
        
        // Delivery location info
        event.setDeliveryAddress(order.getDeliveryAddress());
        event.setDeliveryLat(order.getDeliveryLat());
        event.setDeliveryLng(order.getDeliveryLng());
        
        // Pickup location info
        event.setPickupLat(order.getPickupLat());
        event.setPickupLng(order.getPickupLng());
        
        // Restaurant info
        event.setRestaurantName(order.getRestaurantName());
        event.setRestaurantAddress(order.getRestaurantAddress());
        event.setRestaurantPhone(order.getRestaurantPhone());
        
        // Customer info
        event.setCustomerName(order.getCustomerName());
        event.setCustomerPhone(order.getCustomerPhone());
        event.setNotes(order.getNotes());
        
        // Timestamps
        event.setCreatedAt(order.getCreatedAt());
        event.setCreatorId(order.getCreatorId());
        
        return event;
    }
}
