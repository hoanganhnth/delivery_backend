package com.delivery.order_service.listener;

import com.delivery.order_service.common.constants.KafkaTopicConstants;
import com.delivery.order_service.dto.event.RestaurantEvent;
import com.delivery.order_service.service.OrderEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Restaurant Event Listener theo AI Coding Instructions
 */
@Slf4j
@Component
public class RestaurantEventListener {

    private final OrderEventService orderEventService;

    // ✅ Constructor Injection (MANDATORY)
    public RestaurantEventListener(OrderEventService orderEventService) {
        this.orderEventService = orderEventService;
    }

    /**
     * ✅ Handle restaurant confirmation events
     */
    @KafkaListener(topics = KafkaTopicConstants.RESTAURANT_CONFIRMED_TOPIC)
    public void handleRestaurantConfirmed(
            @Payload RestaurantEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received RestaurantConfirmedEvent: orderId={}, restaurantId={}, estimatedTime={}",
                event.getOrderId(), event.getRestaurantId(), event.getEstimatedPrepTime());

        try {
            orderEventService.handleRestaurantConfirmed(event);
            
            log.info("✅ Successfully processed RestaurantConfirmedEvent for order: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("💥 Failed to process RestaurantConfirmedEvent for order {}: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * ✅ Handle restaurant rejection events
     */
    @KafkaListener(topics = KafkaTopicConstants.RESTAURANT_REJECTED_TOPIC)
    public void handleRestaurantRejected(
            @Payload RestaurantEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received RestaurantRejectedEvent: orderId={}, restaurantId={}, reason={}",
                event.getOrderId(), event.getRestaurantId(), event.getRejectionReason());

        try {
            orderEventService.handleRestaurantRejected(event);
            
            log.info("✅ Successfully processed RestaurantRejectedEvent for order: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("💥 Failed to process RestaurantRejectedEvent for order {}: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
