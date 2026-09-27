package com.delivery.notification_service.listener;

import com.delivery.notification_service.exception.NotificationConflictException;
import com.delivery.notification_service.service.NotificationService;
import com.delivery.observability.SafeLog;
import com.delivery.order.contracts.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Consumes the shared immutable order-created contract. */
@Slf4j
@Component
public class OrderEventListener {

    private final NotificationService notificationService;

    public OrderEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RetryableTopic(
            attempts = "${app.kafka.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.kafka.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.kafka.retry.max-delay-ms:10000}"),
            exclude = {IllegalArgumentException.class, NotificationConflictException.class},
            kafkaTemplate = "commonKafkaTemplate",
            autoCreateTopics = "${app.kafka.retry.auto-create-topics:false}",
            retryTopicSuffix = "-retry-notification",
            dltTopicSuffix = ".notification.DLT")
    @KafkaListener(topics = "${app.kafka.topics.order-created:order.created}")
    public void handleOrderCreatedEvent(
            OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            validateIdentity(event);

            log.info("📥 Received OrderCreatedEvent from topic '{}': orderId={}, userId={}, restaurant={}",
                    topic, event.orderId(), event.userId(), event.restaurantName());

            notificationService.sendOrderCreatedNotification(
                    event.eventId(),
                    event.userId(),
                    event.userPrincipalId(),
                    event.orderId(),
                    event.restaurantName());

            log.info("✅ Successfully processed OrderCreatedEvent for order: {}", event.orderId());
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

    private void validateIdentity(OrderCreatedEvent event) {
        if (event == null || event.eventId() == null || event.orderId() == null || event.orderId() <= 0
                || event.userId() == null || event.userId() <= 0
                || event.restaurantName() == null || event.restaurantName().isBlank()) {
            throw new IllegalArgumentException(
                    "stable eventId, positive order/user IDs and canonical restaurantName are required");
        }
    }
}
