package com.delivery.notification_service.listener;

import com.delivery.notification_service.common.constants.KafkaTopicConstants;
import com.delivery.notification_service.dto.event.DeliveryEvent;
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
 * ✅ Delivery Event Listener — nhận events từ Delivery Service qua Kafka
 * Sử dụng pattern String + ObjectMapper (giống MatchEventListener)
 * Chỉ xử lý delivery status updates — shipper matching được xử lý bởi MatchEventListener
 */
@Slf4j
@Component
public class DeliveryEventListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public DeliveryEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @KafkaListener(topics = KafkaTopicConstants.DELIVERY_STATUS_UPDATED_TOPIC)
    public void handleDeliveryStatusUpdatedEvent(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            DeliveryEvent event = objectMapper.readValue(message, DeliveryEvent.class);

            log.info("📥 Received DeliveryStatusUpdatedEvent from topic '{}': deliveryId={}, orderId={}, userId={}, status={}",
                    topic, event.getDeliveryId(), event.getOrderId(), event.getUserId(), event.getStatus());

            // Validate required fields
            if (event.getUserId() == null) {
                log.warn("⚠️ DeliveryEvent missing userId, skipping notification for delivery: {}", event.getDeliveryId());
                acknowledgment.acknowledge();
                return;
            }

            // Send delivery status notification to customer
            notificationService.sendDeliveryStatusNotification(
                    event.getUserId(),
                    event.getDeliveryId(),
                    event.getStatus(),
                    event.getShipperName() != null ? event.getShipperName() : "Shipper"
            );

            log.info("✅ Successfully processed DeliveryStatusUpdatedEvent for delivery: {}", event.getDeliveryId());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Error processing DeliveryStatusUpdatedEvent - Raw: {} - Error: {}",
                    message, e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
}
