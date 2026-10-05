package com.delivery.notification_service.listener;

import com.delivery.delivery.contracts.DeliveryStatusUpdatedEvent;
import com.delivery.identity.contracts.SimulationContext;
import com.delivery.notification_service.exception.NotificationConflictException;
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
import com.delivery.notification.domain.EventIdentity;
import com.delivery.notification.application.IncomingStatus;
import com.delivery.notification.application.api.StatusEventPort;

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

    @RetryableTopic(
            attempts = "${app.kafka.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${app.kafka.retry.initial-delay-ms:1000}",
                    multiplierExpression = "${app.kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${app.kafka.retry.max-delay-ms:10000}"),
            exclude = {IllegalArgumentException.class, NotificationConflictException.class},
            kafkaTemplate = "commonKafkaTemplate",
            autoCreateTopics = "${app.kafka.retry.auto-create-topics:false}",
            // delivery.status-updated is shared with Saga; notifications need
            // their own retry group and destinations.
            retryTopicSuffix = "-retry-notification",
            dltTopicSuffix = ".notification.DLT")
    @KafkaListener(topics = "${app.kafka.topics.delivery-status-updated:delivery.status-updated}")
    public void handleDeliveryStatusUpdatedEvent(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            DeliveryStatusUpdatedEvent event = objectMapper.readValue(message, DeliveryStatusUpdatedEvent.class);
            new IncomingStatus(new StatusEventPort() {
                public void validateSimulationContext() {
                    SimulationContext.orReal(event.simulationContext()).requireValid();
                }
                public void send(EventIdentity.Status status) {
                    log.info("📥 Received DeliveryStatusUpdatedEvent from topic '{}': deliveryId={}, orderId={}, userId={}, status={}",
                            topic, status.deliveryId(), status.orderId(), status.userId(), status.status());
                    notificationService.sendDeliveryStatusNotification(status.eventId(), status.userId(),
                            status.principalId(), status.deliveryId(), status.status(), status.shipperName());
                }
            }).handle(new EventIdentity.Status(event.eventId(), event.deliveryId(), event.orderId(), event.userId(),
                    event.userPrincipalId(), event.status(), event.shipperName()));

            log.info("✅ Successfully processed DeliveryStatusUpdatedEvent for delivery: {}", event.deliveryId());
            acknowledgment.acknowledge();

        } catch (IllegalArgumentException | NotificationConflictException poison) {
            log.warn("Rejecting poison delivery.status-updated record: topic={}, partition={}, reason={}",
                    topic, partition, poison.getMessage());
            throw poison;
        } catch (Exception e) {
            log.error("💥 Error processing DeliveryStatusUpdatedEvent - topic={}, partition={}, error={}",
                    topic, partition, e.getMessage(), e);
            throw new IllegalStateException("Failed to process delivery.status-updated notification", e);
        }
    }

}
