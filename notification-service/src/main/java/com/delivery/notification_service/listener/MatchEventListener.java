package com.delivery.notification_service.listener;

import com.delivery.notification_service.dto.event.ShipperFoundEvent;
import com.delivery.notification_service.exception.NotificationConflictException;
import com.delivery.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Match Event Listener để nhận events từ Match Service theo Backend Instructions
 * Simplified: Chỉ listen ShipperFoundEvent duy nhất cho dễ quản lý
 */
@Slf4j
@Component
public class MatchEventListener {

    private final NotificationService notificationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public MatchEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Delivery publishes this only after persisting the active offer.
     */
    @RetryableTopic(
            attempts = "${app.kafka.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.kafka.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.kafka.retry.max-delay-ms:10000}"),
            exclude = {IllegalArgumentException.class, NotificationConflictException.class},
            kafkaTemplate = "retryKafkaTemplate",
            autoCreateTopics = "${app.kafka.retry.auto-create-topics:false}",
            retryTopicSuffix = "-retry-notification",
            dltTopicSuffix = ".notification.DLT")
    @KafkaListener(topics = "${app.kafka.topics.shipper-offered:delivery.shipper-offered}")
    public void handleShipperFoundEvent(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            ShipperFoundEvent event = objectMapper.readValue(message, ShipperFoundEvent.class);

            if (event.getAvailableShippers() == null || event.getAvailableShippers().size() != 1) {
                throw new IllegalArgumentException("Invalid single-shipper offer for delivery: "
                        + event.getDeliveryId());
            }
            if (event.getEventId() == null || event.getEventId().isBlank()) {
                throw new IllegalArgumentException("Persisted shipper offer is missing eventId");
            }
            if (event.getDeliveryId() == null || event.getDeliveryId() <= 0
                    || event.getOrderId() == null || event.getOrderId() <= 0) {
                throw new IllegalArgumentException("Persisted shipper offer requires positive delivery/order IDs");
            }
            if (!hasText(event.getRestaurantName()) || !hasText(event.getPickupAddress())
                    || !hasText(event.getDeliveryAddress())) {
                throw new IllegalArgumentException(
                        "Persisted shipper offer requires canonical restaurant and address text");
            }
            ShipperFoundEvent.ShipperMatchResult selected = event.getAvailableShippers().get(0);
            if (selected.getShipperId() == null || selected.getShipperId() <= 0
                    || selected.getDistanceKm() == null || !Double.isFinite(selected.getDistanceKm())
                    || selected.getDistanceKm() < 0) {
                throw new IllegalArgumentException("Persisted shipper offer has invalid shipper/distance identity");
            }

            log.info("📥 Received persisted shipper offer from topic '{}': deliveryId={}, orderId={}",
                    topic, event.getDeliveryId(), event.getOrderId());

            // The persisted-offer contract contains exactly one shipper.
            for (ShipperFoundEvent.ShipperMatchResult shipper : event.getAvailableShippers()) {
                notificationService.sendShipperMatchFoundNotification(
                            shipper.getShipperId(),
                            event.getOrderId(),
                            event.getRestaurantName(),
                            event.getPickupAddress(),
                            event.getDeliveryAddress(),
                            shipper.getDistanceKm(),
                            event.getEventId()
                    );

                log.info("✅ Sent notification to shipper: {} for order: {} (distance: {}km)",
                        shipper.getShipperId(), event.getOrderId(), shipper.getDistanceKm());
            }

            log.info("✅ Successfully processed ShipperFoundEvent for delivery: {} - notified {} shippers", 
                    event.getDeliveryId(), event.getAvailableShippers().size());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException | NotificationConflictException poison) {
            log.warn("Rejecting poison delivery.shipper-offered record: topic={}, partition={}, reason={}",
                    topic, partition, poison.getMessage());
            throw poison;
        } catch (Exception e) {
            log.error("💥 Error processing ShipperFoundEvent - topic={}, partition={}, error={}",
                    topic, partition, e.getMessage(), e);
            throw new IllegalStateException("Failed to process persisted shipper offer", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    
}
