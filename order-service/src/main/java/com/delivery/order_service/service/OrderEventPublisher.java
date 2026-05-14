package com.delivery.order_service.service;

import com.delivery.order_service.common.constants.KafkaTopicConstants;
import com.delivery.order_service.dto.event.OrderCreatedEvent;
import com.delivery.order_service.dto.event.OrderCancelledEvent;
import com.delivery.order_service.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * ✅ Event Publisher Service cho Order Service theo Backend Instructions
 */
@Slf4j
@Service
public class OrderEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // ✅ Constructor Injection Pattern (MANDATORY)
    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Publish OrderCreatedEvent khi order được tạo thành công
     */
    public void publishOrderCreatedEvent(Order order) {
        try {
            OrderCreatedEvent event = mapOrderToEvent(order);
            
            log.info("📤 Publishing OrderCreatedEvent for order: {}", order.getId());
            
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaTopicConstants.ORDER_CREATED_TOPIC,
                order.getId().toString(), // Key: orderId
                event
            );
            
            // Async callback handling
            future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    log.info("✅ Successfully published OrderCreatedEvent for order: {} to partition: {} with offset: {}",
                            order.getId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("💥 Failed to publish OrderCreatedEvent for order: {}", order.getId(), throwable);
                }
            });
            
        } catch (Exception e) {
            log.error("🔥 Error publishing OrderCreatedEvent for order: {}", order.getId(), e);
        }
    }
    
    /**
     * Publish OrderCancelledEvent khi order bị hủy
     */
    public void publishOrderCancelledEvent(Order order, String previousStatus, Long cancelledBy) {
        try {
            OrderCancelledEvent event = mapOrderToCancelledEvent(order, previousStatus, cancelledBy);
            
            log.info("📤 Publishing OrderCancelledEvent for order: {}", order.getId());
            
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaTopicConstants.ORDER_CANCELLED_TOPIC,
                order.getId().toString(), // Key: orderId
                event
            );
            
            // Async callback handling
            future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    log.info("✅ Successfully published OrderCancelledEvent for order: {} to partition: {} with offset: {}",
                            order.getId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("💥 Failed to publish OrderCancelledEvent for order: {}", order.getId(), throwable);
                }
            });
            
        } catch (Exception e) {
            log.error("🔥 Error publishing OrderCancelledEvent for order: {}", order.getId(), e);
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
        event.setStatus(order.getStatus());
        
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
