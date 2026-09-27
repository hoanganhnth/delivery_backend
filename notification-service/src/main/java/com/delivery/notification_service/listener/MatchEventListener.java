package com.delivery.notification_service.listener;

import com.delivery.notification_service.exception.NotificationConflictException;
import com.delivery.notification_service.service.NotificationService;
import com.delivery.delivery.contracts.ShipperFoundEvent;
import com.delivery.identity.contracts.SimulationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes the shared immutable persisted-shipper-offer contract.
 */
@Slf4j
@Component
public class MatchEventListener {

    private final NotificationService notificationService;

    public MatchEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
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
            kafkaTemplate = "commonKafkaTemplate",
            autoCreateTopics = "${app.kafka.retry.auto-create-topics:false}",
            retryTopicSuffix = "-retry-notification",
            dltTopicSuffix = ".notification.DLT")
    @KafkaListener(topics = "${app.kafka.topics.shipper-offered:delivery.shipper-offered}")
    public void handleShipperFoundEvent(
            ShipperFoundEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            if (event == null || event.availableShippers() == null || event.availableShippers().size() != 1) {
                throw new IllegalArgumentException("Invalid single-shipper offer for delivery: "
                        + (event == null ? null : event.deliveryId()));
            }
            if (event.eventId() == null) {
                throw new IllegalArgumentException("Persisted shipper offer is missing eventId");
            }
            if (event.deliveryId() == null || event.deliveryId() <= 0
                    || event.orderId() == null || event.orderId() <= 0) {
                throw new IllegalArgumentException("Persisted shipper offer requires positive delivery/order IDs");
            }
            if (!hasText(event.restaurantName()) || !hasText(event.pickupAddress())
                    || !hasText(event.deliveryAddress())) {
                throw new IllegalArgumentException(
                        "Persisted shipper offer requires canonical restaurant and address text");
            }
            ShipperFoundEvent.ShipperMatchResult selected = event.availableShippers().get(0);
            if (selected.shipperId() == null || selected.shipperId() <= 0
                    || selected.distanceKm() == null || !Double.isFinite(selected.distanceKm())
                    || selected.distanceKm() < 0) {
                throw new IllegalArgumentException("Persisted shipper offer has invalid shipper/distance identity");
            }

            SimulationContext context = SimulationContext.orReal(event.simulationContext());
            context.requireValid();
            if (context.isSimulation()) {
                log.info("Skipping external shipper notification for simulation run {} delivery {}",
                        context.runId(), event.deliveryId());
                acknowledgment.acknowledge();
                return;
            }

            log.info("📥 Received persisted shipper offer from topic '{}': deliveryId={}, orderId={}",
                    topic, event.deliveryId(), event.orderId());

            // The persisted-offer contract contains exactly one shipper.
            for (ShipperFoundEvent.ShipperMatchResult shipper : event.availableShippers()) {
                notificationService.sendShipperMatchFoundNotification(
                            shipper.shipperId(),
                            event.orderId(),
                            event.restaurantName(),
                            event.pickupAddress(),
                            event.deliveryAddress(),
                            shipper.distanceKm(),
                            event.eventId().toString()
                    );

                log.info("✅ Sent notification to shipper: {} for order: {} (distance: {}km)",
                        shipper.shipperId(), event.orderId(), shipper.distanceKm());
            }

            log.info("✅ Successfully processed ShipperFoundEvent for delivery: {} - notified {} shippers", 
                    event.deliveryId(), event.availableShippers().size());

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
