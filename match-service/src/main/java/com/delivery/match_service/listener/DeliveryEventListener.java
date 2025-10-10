package com.delivery.match_service.listener;

import com.delivery.match_service.common.constants.KafkaTopicConstants;
import com.delivery.match_service.dto.event.DeliveryCancelledEvent;
import com.delivery.match_service.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Kafka Listener cho Match Service để xử lý delivery cancellation events
 * Dừng quá trình matching khi delivery bị hủy
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryEventListener {
    
    private final MatchService matchService;
    
    /**
     * ✅ Xử lý sự kiện hủy delivery để dừng quá trình matching
     */
    @KafkaListener(topics = KafkaTopicConstants.DELIVERY_CANCELLED_TOPIC, groupId = "match-service-group")
    public void handleDeliveryCancelled(
            @Payload DeliveryCancelledEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("🛑 Received delivery cancelled event for delivery: {}, order: {} from topic: {} partition: {} timestamp: {}", 
                    event.getDeliveryId(), event.getOrderId(), topic, partition, timestamp);
            
            // Validate event data
            if (event.getDeliveryId() == null || event.getOrderId() == null) {
                log.error("💥 Invalid DeliveryCancelledEvent: deliveryId or orderId is null");
                acknowledgment.acknowledge();
                return;
            }
            
            if (event.isStopMatching()) {
                // Dừng quá trình matching cho delivery này
                matchService.stopMatchingProcess(event.getDeliveryId(), event.getOrderId(), event.getReason());
                
                log.info("✅ Successfully stopped matching process for delivery: {}", 
                        event.getDeliveryId());
            } else {
                log.info("📝 Delivery cancellation event for delivery: {} does not require stopping matching", 
                        event.getDeliveryId());
            }
            
            // Manual acknowledgment
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("💥 Error handling delivery cancelled event for delivery: {}: {}", 
                     event.getDeliveryId(), e.getMessage(), e);
                     
            // Acknowledge để tránh infinite retry
            acknowledgment.acknowledge();
        }
    }
}
