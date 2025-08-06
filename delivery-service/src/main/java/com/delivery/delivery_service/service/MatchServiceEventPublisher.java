package com.delivery.delivery_service.service;

import com.delivery.delivery_service.common.constants.KafkaTopicConstants;
import com.delivery.delivery_service.dto.event.FindShipperEvent;
import com.delivery.delivery_service.dto.event.MatchAcceptedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * ✅ Event Publisher để gửi events đến Match Service theo AI Instructions
 * Sử dụng constructor injection và async publishing với callbacks
 */
@Slf4j
@Service
public class MatchServiceEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // ✅ Constructor Injection Pattern (MANDATORY)
    public MatchServiceEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Gửi FindShipperEvent đến Match Service để tìm shipper phù hợp
     */
    public void publishFindShipperEvent(FindShipperEvent event) {
        try {
            log.info("🚀 Publishing FindShipperEvent for delivery: {} to Match Service", event.getDeliveryId());
            
            // ✅ Async publishing with callbacks for reliability
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                    KafkaTopicConstants.FIND_SHIPPER_TOPIC,
                    event.getDeliveryId().toString(),
                    event
            );
            
            // Success callback
            future.thenAccept(result -> {
                log.info("✅ Successfully published FindShipperEvent for delivery: {} to partition: {} offset: {}",
                        event.getDeliveryId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            });
            
            // Error callback
            future.exceptionally(throwable -> {
                log.error("💥 Failed to publish FindShipperEvent for delivery: {} - Error: {}",
                        event.getDeliveryId(), throwable.getMessage(), throwable);
                
                // TODO: Implement retry logic or save to dead letter queue
                return null;
            });
            
        } catch (Exception e) {
            log.error("🔥 Unexpected error while publishing FindShipperEvent for delivery: {} - Error: {}",
                    event.getDeliveryId(), e.getMessage(), e);
        }
    }
    
    /**
     * ✅ Gửi ShipperAcceptedEvent đến Notification Service khi shipper accept đơn
     */
    public void publishShipperAcceptedEvent(MatchAcceptedEvent event) {
        try {
            log.info("🚀 Publishing ShipperAcceptedEvent for order: {} shipper: {} to Notification Service", 
                    event.getOrderId(), event.getShipperId());
            
            // ✅ Async publishing với callbacks
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                    KafkaTopicConstants.SHIPPER_ACCEPTED_TOPIC,
                    event.getOrderId().toString(),
                    event
            );
            
            // Success callback
            future.thenAccept(result -> {
                log.info("✅ Successfully published ShipperAcceptedEvent for order: {} to partition: {} offset: {}",
                        event.getOrderId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            });
            
            // Error callback
            future.exceptionally(throwable -> {
                log.error("💥 Failed to publish ShipperAcceptedEvent for order: {} - Error: {}",
                        event.getOrderId(), throwable.getMessage(), throwable);
                
                // TODO: Implement retry logic hoặc save to dead letter queue
                return null;
            });
            
        } catch (Exception e) {
            log.error("🔥 Unexpected error while publishing ShipperAcceptedEvent for order: {} - Error: {}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }
    
    /**
     * Gửi delivery status update event (có thể dùng sau này)
     */
    public void publishDeliveryStatusUpdated(Long deliveryId, String status, String previousStatus) {
        try {
            log.info("📊 Publishing delivery status update: {} -> {} for delivery: {}",
                    previousStatus, status, deliveryId);
            
            // Create status update event
            DeliveryStatusUpdateEvent statusEvent = new DeliveryStatusUpdateEvent(
                    deliveryId, status, previousStatus
            );
            
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                    KafkaTopicConstants.DELIVERY_STATUS_UPDATED_TOPIC,
                    deliveryId.toString(),
                    statusEvent
            );
            
            future.thenAccept(result -> 
                log.info("✅ Published delivery status update for delivery: {}", deliveryId)
            ).exceptionally(throwable -> {
                log.error("💥 Failed to publish status update for delivery: {} - Error: {}",
                        deliveryId, throwable.getMessage());
                return null;
            });
            
        } catch (Exception e) {
            log.error("🔥 Error publishing status update for delivery: {} - Error: {}",
                    deliveryId, e.getMessage(), e);
        }
    }
    
    /**
     * Inner class for delivery status update events
     */
    public static class DeliveryStatusUpdateEvent {
        public final Long deliveryId;
        public final String newStatus;
        public final String oldStatus;
        public final String eventType = "DELIVERY_STATUS_UPDATED";
        public final java.time.LocalDateTime timestamp = java.time.LocalDateTime.now();
        
        public DeliveryStatusUpdateEvent(Long deliveryId, String newStatus, String oldStatus) {
            this.deliveryId = deliveryId;
            this.newStatus = newStatus;
            this.oldStatus = oldStatus;
        }
    }
}
