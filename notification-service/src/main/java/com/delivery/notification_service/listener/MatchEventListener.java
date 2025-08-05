package com.delivery.notification_service.listener;

import com.delivery.notification_service.common.constants.KafkaTopicConstants;
import com.delivery.notification_service.dto.event.MatchEvent;
import com.delivery.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Match Event Listener để nhận events từ Match Service theo Backend Instructions
 * Match Service sẽ publish events với topic "match.shipper-matched" khi tìm thấy shipper phù hợp
 * Topics đã được align với Match Service KafkaTopicConstants
 */
@Slf4j
@Component
public class MatchEventListener {

    private final NotificationService notificationService;

    public MatchEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * ✅ Lắng nghe khi Match Service tìm thấy shipper phù hợp (topic: match.shipper-matched)
     */
    @KafkaListener(topics = KafkaTopicConstants.MATCH_FOUND_TOPIC)
    public void handleMatchFoundEvent(
            @Payload MatchEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received MatchFoundEvent from topic '{}': matchId={}, orderId={}, shipperId={}, shipperName={}, distance={}km",
                topic, event.getMatchId(), event.getOrderId(), event.getShipperId(), event.getShipperName(), event.getDistance());

        try {
            // Thông báo cho shipper về order mới phù hợp
            notificationService.sendShipperMatchFoundNotification(
                    event.getShipperId(),
                    event.getOrderId(),
                    event.getRestaurantName(),
                    event.getPickupAddress(),
                    event.getDeliveryAddress(),
                    event.getDistance(),
                    event.getEstimatedPrice(),
                    event.getEstimatedTime()
            );

            log.info("✅ Successfully processed MatchFoundEvent for shipper: {}", event.getShipperId());

        } catch (Exception e) {
            log.error("💥 Failed to process MatchFoundEvent for match {}: {}", event.getMatchId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.MATCH_REQUEST_TOPIC)
    public void handleMatchRequestEvent(
            @Payload MatchEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received MatchRequestEvent: matchId={}, orderId={}, shipperId={}, shipperName={}",
                event.getMatchId(), event.getOrderId(), event.getShipperId(), event.getShipperName());

        try {
            // Thông báo cho shipper về request giao hàng
            notificationService.sendShipperDeliveryRequestNotification(
                    event.getShipperId(),
                    event.getOrderId(),
                    event.getRestaurantName(),
                    event.getCustomerName(),
                    event.getPickupAddress(),
                    event.getDeliveryAddress(),
                    event.getOrderValue(),
                    event.getEstimatedPrice(),
                    event.getEstimatedTime()
            );

            log.info("✅ Successfully processed MatchRequestEvent for shipper: {}", event.getShipperId());

        } catch (Exception e) {
            log.error("💥 Failed to process MatchRequestEvent for match {}: {}", event.getMatchId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.MATCH_ACCEPTED_TOPIC)
    public void handleMatchAcceptedEvent(
            @Payload MatchEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received MatchAcceptedEvent: matchId={}, orderId={}, shipperId={}, shipperName={}",
                event.getMatchId(), event.getOrderId(), event.getShipperId(), event.getShipperName());

        try {
            // Thông báo cho customer về việc shipper đã nhận đơn
            notificationService.sendCustomerShipperAcceptedNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getShipperName(),
                    event.getShipperPhone(),
                    event.getEstimatedTime()
            );

            // Thông báo cho shipper xác nhận đã nhận đơn
            notificationService.sendShipperConfirmationNotification(
                    event.getShipperId(),
                    event.getOrderId(),
                    event.getRestaurantName(),
                    event.getPickupAddress(),
                    event.getCustomerPhone()
            );

            log.info("✅ Successfully processed MatchAcceptedEvent for match: {}", event.getMatchId());

        } catch (Exception e) {
            log.error("💥 Failed to process MatchAcceptedEvent for match {}: {}", event.getMatchId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.MATCH_REJECTED_TOPIC)
    public void handleMatchRejectedEvent(
            @Payload MatchEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) String offset) {

        log.info("📥 Received MatchRejectedEvent: matchId={}, orderId={}, shipperId={}, reason={}",
                event.getMatchId(), event.getOrderId(), event.getShipperId(), event.getReason());

        try {
            // Log rejection for analytics
            log.info("📊 Shipper {} rejected order {} - reason: {}", 
                    event.getShipperId(), event.getOrderId(), event.getReason());

            // Optional: Send confirmation to shipper about rejection
            notificationService.sendShipperRejectionConfirmationNotification(
                    event.getShipperId(),
                    event.getOrderId(),
                    event.getReason()
            );

            log.info("✅ Successfully processed MatchRejectedEvent for match: {}", event.getMatchId());

        } catch (Exception e) {
            log.error("💥 Failed to process MatchRejectedEvent for match {}: {}", event.getMatchId(), e.getMessage(), e);
        }
    }
}
