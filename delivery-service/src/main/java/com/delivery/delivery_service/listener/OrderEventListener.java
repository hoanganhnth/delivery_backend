package com.delivery.delivery_service.listener;

import com.delivery.delivery_service.common.constants.KafkaTopicConstants;
import com.delivery.delivery_service.dto.event.OrderCreatedEvent;
import com.delivery.delivery_service.dto.event.OrderCancelledEvent;
import com.delivery.delivery_service.dto.event.ShipperNotFoundEvent;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.delivery_service.service.EventValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Kafka Event Listener cho Delivery Service theo Backend Instructions
 * Lắng nghe OrderCreatedEvent từ Order Service với comprehensive validation
 */
@Slf4j
@Component
public class OrderEventListener {
    
    private final DeliveryService deliveryService;
    private final EventValidationService eventValidationService;
    
    // ✅ Constructor Injection Pattern (MANDATORY)
    public OrderEventListener(DeliveryService deliveryService, 
                             EventValidationService eventValidationService) {
        this.deliveryService = deliveryService;
        this.eventValidationService = eventValidationService;
    }
    
    /**
     * Lắng nghe OrderCreatedEvent và tự động tạo Delivery record
     */
    @KafkaListener(topics = KafkaTopicConstants.ORDER_CREATED_TOPIC)
    public void handleOrderCreatedEvent(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("📥 Received OrderCreatedEvent for order: {} from topic: {} partition: {} timestamp: {}",
                    event.getOrderId(), topic, partition, timestamp);
            
            // ✅ Validate event data trước khi process
            EventValidationService.ValidationResult validationResult = 
                    eventValidationService.validateOrderCreatedEvent(event);
            
            if (!validationResult.isValid()) {
                log.error("💥 Invalid OrderCreatedEvent for order: {} - Errors: {}", 
                         event.getOrderId(), validationResult.getErrorMessage());
                
                // Check if có minimum required fields để fallback processing
                if (eventValidationService.hasMinimumRequiredFields(event)) {
                    log.warn("🔄 Attempting fallback processing với minimum required fields for order: {}", 
                            event.getOrderId());
                    // Continue processing với degraded data quality
                } else {
                    log.error("🚫 Order {} không có minimum required fields, skipping processing", 
                             event.getOrderId());
                    acknowledgment.acknowledge();
                    return;
                }
            }
            
            // ✅ Tự động tạo delivery record cho order mới
            deliveryService.createDeliveryFromOrderEvent(event);
            
            log.info("✅ Successfully processed OrderCreatedEvent for order: {}", event.getOrderId());
            
            // Manual acknowledgment
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("💥 Error processing OrderCreatedEvent for order: {} - Error: {}", 
                     event.getOrderId(), e.getMessage(), e);
            
            // Trong trường hợp lỗi, có thể implement retry logic hoặc send to DLQ
            // Hiện tại sẽ acknowledge để tránh infinite retry
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * Lắng nghe OrderCancelledEvent và ngừng tìm kiếm shipper
     */
    @KafkaListener(topics = KafkaTopicConstants.ORDER_CANCELLED_TOPIC)
    public void handleOrderCancelledEvent(
            @Payload OrderCancelledEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("📥 Received OrderCancelledEvent for order: {} from topic: {} partition: {} timestamp: {}",
                    event.getOrderId(), topic, partition, timestamp);
            
            // ✅ Validate event data trước khi process
            if (event.getOrderId() == null) {
                log.error("💥 Invalid OrderCancelledEvent: orderId is null");
                acknowledgment.acknowledge();
                return;
            }
            
            // ✅ Ngừng tìm kiếm shipper và hủy delivery record nếu có
            deliveryService.cancelDeliveryFromOrderCancelledEvent(event);
            
            log.info("✅ Successfully processed OrderCancelledEvent for order: {} - Stopped shipper search", 
                    event.getOrderId());
            
            // Manual acknowledgment
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("💥 Error processing OrderCancelledEvent for order: {} - Error: {}", 
                     event.getOrderId(), e.getMessage(), e);
            
            // Acknowledge để tránh infinite retry
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * ✅ Lắng nghe ShipperNotFoundEvent từ match-service và cập nhật delivery status
     */
    @KafkaListener(topics = KafkaTopicConstants.SHIPPER_NOT_FOUND_TOPIC)
    public void handleShipperNotFoundEvent(
            @Payload ShipperNotFoundEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("📥 Received ShipperNotFoundEvent for delivery: {}, order: {} from topic: {} partition: {} timestamp: {}",
                    event.getDeliveryId(), event.getOrderId(), topic, partition, timestamp);
            
            // ✅ Validate event data
            if (event.getDeliveryId() == null || event.getOrderId() == null) {
                log.error("💥 Invalid ShipperNotFoundEvent: deliveryId or orderId is null");
                acknowledgment.acknowledge();
                return;
            }
            
            // ✅ Cập nhật delivery status thành SHIPPER_NOT_FOUND
            deliveryService.updateDeliveryStatusFromShipperNotFoundEvent(event);
            
            log.info("✅ Successfully processed ShipperNotFoundEvent for delivery: {} - Updated status to SHIPPER_NOT_FOUND", 
                    event.getDeliveryId());
            
            // Manual acknowledgment
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("💥 Error processing ShipperNotFoundEvent for delivery: {} - Error: {}", 
                     event.getDeliveryId(), e.getMessage(), e);
            
            // Acknowledge để tránh infinite retry
            acknowledgment.acknowledge();
        }
    }
}
