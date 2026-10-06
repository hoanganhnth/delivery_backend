package com.delivery.notification_service.listener;

import com.delivery.notification_service.exception.NotificationConflictException;
import com.delivery.notification_service.service.NotificationService;
import com.delivery.notification.domain.EventIdentity;
import com.delivery.notification.application.IncomingOffer;
import com.delivery.notification.application.api.OfferEventPort;
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
            var offer = event == null ? null : new EventIdentity.Offer(
                    event.eventId(), event.deliveryId(), event.orderId(), event.restaurantName(),
                    event.pickupAddress(), event.deliveryAddress(), event.availableShippers() == null ? null
                            : event.availableShippers().stream().map(shipper -> shipper == null ? null
                                    : new EventIdentity.SelectedShipper(shipper.shipperId(), shipper.distanceKm())).toList());
            new IncomingOffer(new OfferEventPort() {
                private SimulationContext context;
                public void validateSimulationContext() {
                    context = SimulationContext.orReal(event.simulationContext());
                    context.requireValid();
                }
                public boolean isSimulation() {
                    if (context.isSimulation()) log.info("Skipping external shipper notification for simulation run {} delivery {}",
                            context.runId(), event.deliveryId());
                    return context.isSimulation();
                }
                public void send(EventIdentity.Offer incoming, EventIdentity.SelectedShipper selected) {
                    log.info("📥 Received persisted shipper offer from topic '{}': deliveryId={}, orderId={}",
                            topic, incoming.deliveryId(), incoming.orderId());
                    notificationService.sendShipperMatchFoundNotification(selected.shipperId(), incoming.orderId(),
                            incoming.restaurantName(), incoming.pickupAddress(), incoming.deliveryAddress(),
                            selected.distanceKm(), incoming.eventId().toString());
                    log.info("✅ Sent notification to shipper: {} for order: {} (distance: {}km)",
                            selected.shipperId(), incoming.orderId(), selected.distanceKm());
                    log.info("✅ Successfully processed ShipperFoundEvent for delivery: {} - notified {} shippers",
                            incoming.deliveryId(), incoming.shippers().size());
                }
            }).handle(offer);

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

}
