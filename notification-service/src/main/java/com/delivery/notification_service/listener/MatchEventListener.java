package com.delivery.notification_service.listener;

import com.delivery.notification_service.common.constants.KafkaTopicConstants;
import com.delivery.notification_service.dto.event.ShipperFoundEvent;
import com.delivery.notification_service.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * ✅ Match Event Listener để nhận events từ Match Service theo Backend Instructions
 * Simplified: Chỉ listen ShipperFoundEvent duy nhất cho dễ quản lý
 */
@Slf4j
@Component
public class MatchEventListener {

    private final NotificationService notificationService;

    public MatchEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * ✅ Lắng nghe ShipperFoundEvent từ match-service để notify shippers
     */
    @KafkaListener(topics = KafkaTopicConstants.SHIPPER_FOUND_TOPIC)
    public void handleShipperFoundEvent(
            @Payload ShipperFoundEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {

        try {
            log.info("📥 Received ShipperFoundEvent from topic '{}': deliveryId={}, orderId={}, {} shippers found",
                    topic, event.getDeliveryId(), event.getOrderId(), event.getAvailableShippers().size());

            // ✅ Validate event data
            if (event.getAvailableShippers() == null || event.getAvailableShippers().isEmpty()) {
                log.warn("⚠️ No shippers in ShipperFoundEvent for delivery: {}", event.getDeliveryId());
                acknowledgment.acknowledge();
                return;
            }

            // ✅ Notify each available shipper về order mới
            for (ShipperFoundEvent.ShipperMatchResult shipper : event.getAvailableShippers()) {
                try {
                    notificationService.sendShipperMatchFoundNotification(
                            shipper.getShipperId(),
                            event.getOrderId(),
                            event.getRestaurantName() != null ? event.getRestaurantName() : "Restaurant",
                            event.getPickupAddress() != null ? event.getPickupAddress() : "Pickup location",
                            event.getDeliveryAddress() != null ? event.getDeliveryAddress() : "Delivery location",
                            shipper.getDistanceKm(),
                            calculateEstimatedPrice(shipper.getDistanceKm()).doubleValue(),
                            calculateEstimatedTime(shipper.getDistanceKm())
                    );
                    
                    log.info("✅ Sent notification to shipper: {} for order: {} (distance: {}km)", 
                            shipper.getShipperId(), event.getOrderId(), shipper.getDistanceKm());
                    
                } catch (Exception e) {
                    log.error("💥 Failed to notify shipper: {} for order: {} - Error: {}", 
                             shipper.getShipperId(), event.getOrderId(), e.getMessage(), e);
                }
            }

            log.info("✅ Successfully processed ShipperFoundEvent for delivery: {} - notified {} shippers", 
                    event.getDeliveryId(), event.getAvailableShippers().size());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("💥 Error processing ShipperFoundEvent for delivery: {} - Error: {}", 
                     event.getDeliveryId(), e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * ✅ Calculate estimated delivery price based on distance
     */
    private BigDecimal calculateEstimatedPrice(Double distanceKm) {
        if (distanceKm == null || distanceKm <= 0) {
            return BigDecimal.valueOf(20000); // Default 20k VND
        }
        
        // Simple pricing: 15k base + 5k per km
        double basePrice = 15000;
        double pricePerKm = 5000;
        double totalPrice = basePrice + (distanceKm * pricePerKm);
        
        return BigDecimal.valueOf(Math.ceil(totalPrice / 1000) * 1000); // Round up to nearest 1k
    }
    
    /**
     * ✅ Calculate estimated delivery time based on distance
     */
    private Integer calculateEstimatedTime(Double distanceKm) {
        if (distanceKm == null || distanceKm <= 0) {
            return 30; // Default 30 minutes
        }
        
        // Simple estimation: 15 minutes base + 5 minutes per km (assuming city traffic)
        int baseTime = 15;
        int timePerKm = 5;
        int totalTime = baseTime + (int) Math.ceil(distanceKm * timePerKm);
        
        return Math.max(totalTime, 20); // Minimum 20 minutes
    }
}
