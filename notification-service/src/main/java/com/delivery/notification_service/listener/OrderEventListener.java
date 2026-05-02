package com.delivery.notification_service.listener;

import com.delivery.notification_service.common.constants.KafkaTopicConstants;
import com.delivery.notification_service.dto.event.OrderEvent;
import com.delivery.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * ✅ Order Event Listener — nhận events từ Order Service qua Kafka
 * Sử dụng pattern String + ObjectMapper (giống MatchEventListener)
 */
@Slf4j
@Component
public class OrderEventListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public OrderEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @KafkaListener(topics = KafkaTopicConstants.ORDER_CREATED_TOPIC)
    public void handleOrderCreatedEvent(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

            log.info("📥 Received OrderCreatedEvent from topic '{}': orderId={}, userId={}, restaurant={}",
                    topic, event.getOrderId(), event.getUserId(), event.getRestaurantName());

            // Send notification to customer
            notificationService.sendOrderCreatedNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getRestaurantName() != null ? event.getRestaurantName() : "Nhà hàng"
            );

            log.info("✅ Successfully processed OrderCreatedEvent for order: {}", event.getOrderId());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Error processing OrderCreatedEvent - Raw: {} - Error: {}",
                    message, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.ORDER_STATUS_UPDATED_TOPIC)
    public void handleOrderStatusUpdatedEvent(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

            log.info("📥 Received OrderStatusUpdatedEvent from topic '{}': orderId={}, userId={}, status={}, restaurant={}",
                    topic, event.getOrderId(), event.getUserId(), event.getStatus(), event.getRestaurantName());

            // Send status update notification to customer
            notificationService.sendOrderStatusNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getStatus(),
                    event.getRestaurantName() != null ? event.getRestaurantName() : "Nhà hàng"
            );

            log.info("✅ Successfully processed OrderStatusUpdatedEvent for order: {}", event.getOrderId());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Error processing OrderStatusUpdatedEvent - Raw: {} - Error: {}",
                    message, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
}
