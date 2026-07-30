package com.delivery.notification_service.listener;

import com.delivery.notification_service.dto.event.DeliveryEvent;
import com.delivery.notification_service.service.NotificationService;
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
import java.util.Set;

/**
 * ✅ Delivery Event Listener — nhận events từ Delivery Service qua Kafka
 * Sử dụng pattern String + ObjectMapper (giống MatchEventListener)
 * Chỉ xử lý delivery status updates — shipper matching được xử lý bởi MatchEventListener
 */
@Slf4j
@Component
public class DeliveryEventListener {

    private static final Set<String> CANONICAL_STATUSES = Set.of(
            "PENDING", "FINDING_SHIPPER", "WAIT_SHIPPER_CONFIRM", "SHIPPER_NOT_FOUND",
            "ASSIGNED", "PICKED_UP", "DELIVERING", "DELIVERED", "CANCELLED");

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public DeliveryEventListener(NotificationService notificationService) {
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
            exclude = IllegalArgumentException.class,
            kafkaTemplate = "retryKafkaTemplate",
            autoCreateTopics = "${app.kafka.retry.auto-create-topics:false}",
            dltTopicSuffix = ".DLT")
    @KafkaListener(topics = "${app.kafka.topics.delivery-status-updated:delivery.status-updated}")
    public void handleDeliveryStatusUpdatedEvent(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            DeliveryEvent event = objectMapper.readValue(message, DeliveryEvent.class);
            if (event.getEventId() == null || event.getDeliveryId() == null || event.getDeliveryId() <= 0
                    || event.getOrderId() == null || event.getOrderId() <= 0
                    || event.getUserId() == null || event.getUserId() <= 0
                    || event.getStatus() == null || !CANONICAL_STATUSES.contains(event.getStatus())) {
                throw new IllegalArgumentException(
                        "stable eventId, positive delivery/order/user IDs and status are required");
            }

            log.info("📥 Received DeliveryStatusUpdatedEvent from topic '{}': deliveryId={}, orderId={}, userId={}, status={}",
                    topic, event.getDeliveryId(), event.getOrderId(), event.getUserId(), event.getStatus());

            // Validate required fields
            // Send delivery status notification to customer
            notificationService.sendDeliveryStatusNotification(
                    event.getEventId(),
                    event.getUserId(),
                    event.getDeliveryId(),
                    event.getStatus(),
                    hasText(event.getShipperName()) ? event.getShipperName() : null
            );

            log.info("✅ Successfully processed DeliveryStatusUpdatedEvent for delivery: {}", event.getDeliveryId());
            acknowledgment.acknowledge();

        } catch (IllegalArgumentException poison) {
            log.warn("Rejecting poison delivery.status-updated record: topic={}, partition={}, reason={}",
                    topic, partition, poison.getMessage());
            throw poison;
        } catch (Exception e) {
            log.error("💥 Error processing DeliveryStatusUpdatedEvent - topic={}, partition={}, error={}",
                    topic, partition, e.getMessage(), e);
            throw new IllegalStateException("Failed to process delivery.status-updated notification", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
