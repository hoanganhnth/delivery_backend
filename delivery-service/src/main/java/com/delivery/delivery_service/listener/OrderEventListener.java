package com.delivery.delivery_service.listener;

import com.delivery.delivery_service.common.constants.KafkaTopicConstants;
import com.delivery.delivery_service.dto.event.OrderCreatedEvent;
import com.delivery.delivery_service.service.DeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Kafka Event Listener cho Delivery Service theo Backend Instructions
 * Lắng nghe OrderCreatedEvent từ Order Service
 */
@Slf4j
@Component
public class OrderEventListener {
    
    private final DeliveryService deliveryService;
    
    // ✅ Constructor Injection Pattern (MANDATORY)
    public OrderEventListener(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
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
            
            // Tự động tạo delivery record cho order mới
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
}
