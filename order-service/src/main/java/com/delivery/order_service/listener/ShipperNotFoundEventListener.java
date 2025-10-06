package com.delivery.order_service.listener;

import com.delivery.order_service.common.constants.KafkaTopicConstants;
import com.delivery.order_service.dto.event.ShipperNotFoundEvent;
import com.delivery.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Kafka Listener cho Order Service để xử lý shipper not found events
 * Cập nhật order status khi không tìm được shipper
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipperNotFoundEventListener {
    
    private final OrderService orderService;
    
    /**
     * ✅ Xử lý sự kiện không tìm được shipper để cập nhật order status
     */
    @KafkaListener(topics = KafkaTopicConstants.SHIPPER_NOT_FOUND_TOPIC, groupId = "order-service-group")
    public void handleShipperNotFoundEvent(
            @Payload ShipperNotFoundEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("📥 Received ShipperNotFoundEvent for order: {}, delivery: {} from topic: {} partition: {} timestamp: {}", 
                    event.getOrderId(), event.getDeliveryId(), topic, partition, timestamp);
            
            // Validate event data
            if (event.getOrderId() == null || event.getDeliveryId() == null) {
                log.error("💥 Invalid ShipperNotFoundEvent: orderId or deliveryId is null");
                acknowledgment.acknowledge();
                return;
            }
            
            // Cập nhật order status để reflect việc không tìm được shipper
            orderService.updateOrderStatusFromShipperNotFoundEvent(event);
            
            log.info("✅ Successfully processed ShipperNotFoundEvent for order: {} - Updated status appropriately", 
                    event.getOrderId());
            
            // Manual acknowledgment
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("💥 Error handling ShipperNotFoundEvent for order: {}: {}", 
                     event.getOrderId(), e.getMessage(), e);
                     
            // Acknowledge để tránh infinite retry
            acknowledgment.acknowledge();
        }
    }
}
