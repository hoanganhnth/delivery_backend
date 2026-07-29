package com.delivery.match_service.service;

import com.delivery.match_service.common.constants.KafkaTopicConstants;
import com.delivery.match_service.dto.event.ShipperNotFoundEvent;
import com.delivery.match_service.dto.event.ShipperFoundEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * ✅ Event Publisher cho Match Service
 * Bắn events khi không tìm được shipper
 */
@Service
@Slf4j
public class MatchEventPublisher {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    // ✅ Constructor Injection Pattern (MANDATORY)
    public MatchEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * ✅ Bắn event khi không tìm được shipper sau nhiều lần retry
     */
    public void publishShipperNotFoundEvent(ShipperNotFoundEvent event) {
        requireStableMetadata(event == null ? null : event.getEventId(),
                event == null ? null : event.getDeliveryId(),
                event == null ? null : event.getOrderId());
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            String key = event.getOrderId().toString();
            
            log.info("📤 Publishing ShipperNotFoundEvent for delivery: {} to topic: {}", 
                    event.getDeliveryId(), KafkaTopicConstants.SHIPPER_NOT_FOUND_TOPIC);
            
            kafkaTemplate.send(KafkaTopicConstants.SHIPPER_NOT_FOUND_TOPIC, key, eventJson)
                    .get(10, TimeUnit.SECONDS);
            log.info("✅ Successfully published ShipperNotFoundEvent for delivery: {}", event.getDeliveryId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing ShipperNotFoundEvent", e);
        } catch (Exception e) {
            log.error("💥 Error serializing ShipperNotFoundEvent for delivery: {}: {}", 
                     event.getDeliveryId(), e.getMessage(), e);
            throw new IllegalStateException("Failed to publish ShipperNotFoundEvent", e);
        }
    }
    
    /**
     * ✅ Bắn event khi tìm được shipper thành công
     */
    public void publishShipperFoundEvent(ShipperFoundEvent event) {
        requireStableMetadata(event == null ? null : event.getEventId(),
                event == null ? null : event.getDeliveryId(),
                event == null ? null : event.getOrderId());
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            String key = event.getOrderId().toString();
            
            log.info("📤 Publishing ShipperFoundEvent for delivery: {} to topic: {}", 
                    event.getDeliveryId(), KafkaTopicConstants.SHIPPER_FOUND_TOPIC);
            
            kafkaTemplate.send(KafkaTopicConstants.SHIPPER_FOUND_TOPIC, key, eventJson)
                    .get(10, TimeUnit.SECONDS);
            log.info("✅ Successfully published ShipperFoundEvent for delivery: {} - {} shippers found",
                    event.getDeliveryId(), event.getAvailableShippers().size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing ShipperFoundEvent", e);
        } catch (Exception e) {
            log.error("💥 Error serializing ShipperFoundEvent for delivery: {}: {}", 
                     event.getDeliveryId(), e.getMessage(), e);
            throw new IllegalStateException("Failed to publish ShipperFoundEvent", e);
        }
    }

    private void requireStableMetadata(String eventId, Long deliveryId, Long orderId) {
        if (eventId == null || eventId.isBlank() || deliveryId == null || deliveryId <= 0
                || orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Stable eventId and positive delivery/order IDs are required");
        }
        java.util.UUID.fromString(eventId);
    }
}
