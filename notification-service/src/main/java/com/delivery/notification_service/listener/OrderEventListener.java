package com.delivery.notification_service.listener;

import com.delivery.notification_service.common.constants.KafkaTopicConstants;
import com.delivery.notification_service.dto.event.OrderEvent;
import com.delivery.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Order Event Listener để nhận events từ Order Service theo Backend Instructions
 */
@Slf4j
@Component
public class OrderEventListener {

    private final NotificationService notificationService;

    public OrderEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = KafkaTopicConstants.ORDER_CREATED_TOPIC)
    public void handleOrderCreatedEvent(
            @Payload OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received OrderCreatedEvent: orderId={}, userId={}, restaurantName={}",
                event.getOrderId(), event.getUserId(), event.getRestaurantName());

        try {
            // Send notification to user
            notificationService.sendOrderCreatedNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getRestaurantName()
            );

            log.info("✅ Successfully processed OrderCreatedEvent for order: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("💥 Failed to process OrderCreatedEvent for order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.ORDER_STATUS_UPDATED_TOPIC)
    public void handleOrderStatusUpdatedEvent(
            @Payload OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received OrderStatusUpdatedEvent: orderId={}, userId={}, status={}, restaurantName={}",
                event.getOrderId(), event.getUserId(), event.getStatus(), event.getRestaurantName());

        try {
            // Send status update notification to user
            notificationService.sendOrderStatusNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getStatus(),
                    event.getRestaurantName()
            );

            log.info("✅ Successfully processed OrderStatusUpdatedEvent for order: {}", event.getOrderId());

        } catch (Exception e) {
            log.error("💥 Failed to process OrderStatusUpdatedEvent for order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }
}
