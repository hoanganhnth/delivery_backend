package com.delivery.notification_service.listener;

import com.delivery.notification_service.dto.event.OrderEvent;
import com.delivery.notification_service.exception.NotificationConflictException;
import com.delivery.notification_service.service.NotificationService;
import com.delivery.observability.SafeLog;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
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

    @RetryableTopic(
            attempts = "${app.kafka.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.kafka.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.kafka.retry.max-delay-ms:10000}"),
            exclude = {IllegalArgumentException.class, NotificationConflictException.class},
            kafkaTemplate = "retryKafkaTemplate",
            autoCreateTopics = "${app.kafka.retry.auto-create-topics:false}",
            // order.created is shared with Saga; notifications need their own
            // retry group and destinations.
            retryTopicSuffix = "-retry-notification",
            dltTopicSuffix = ".notification.DLT")
    @KafkaListener(topics = "${app.kafka.topics.order-created:order.created}")
    public void handleOrderCreatedEvent(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            validateIdentity(event);

            log.info("📥 Received OrderCreatedEvent from topic '{}': orderId={}, userId={}, restaurant={}",
                    topic, event.getOrderId(), event.getUserId(), event.getRestaurantName());

            // Send notification to customer
            notificationService.sendOrderCreatedNotification(
                    event.getEventId(),
                    event.getUserId(),
                    event.getUserPrincipalId(),
                    event.getOrderId(),
                    event.getRestaurantName()
            );

            log.info("✅ Successfully processed OrderCreatedEvent for order: {}", event.getOrderId());
            acknowledgment.acknowledge();

        } catch (IllegalArgumentException | NotificationConflictException poison) {
            log.warn("Rejecting poison order.created record: topic={}, partition={}, reason={}",
                    topic, partition, SafeLog.exceptionMessage(poison));
            throw poison;
        } catch (Exception e) {
            log.error("Order-created notification failed: topic={}, partition={}, reason={}",
                    topic, partition, SafeLog.exceptionMessage(e));
            throw new IllegalStateException("Failed to process order.created notification", e);
        }
    }

    private void validateIdentity(OrderEvent event) {
        if (event.getEventId() == null || event.getOrderId() == null || event.getOrderId() <= 0
                || event.getUserId() == null || event.getUserId() <= 0
                || event.getRestaurantName() == null || event.getRestaurantName().isBlank()) {
            throw new IllegalArgumentException(
                    "stable eventId, positive order/user IDs and canonical restaurantName are required");
        }
    }
}
