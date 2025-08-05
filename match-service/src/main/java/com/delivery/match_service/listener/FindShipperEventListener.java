package com.delivery.match_service.listener;

import com.delivery.match_service.common.constants.KafkaTopicConstants;
import com.delivery.match_service.dto.event.FindShipperEvent;
import com.delivery.match_service.dto.request.FindNearbyShippersRequest;
import com.delivery.match_service.service.MatchService;
import com.delivery.match_service.service.MatchEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ✅ Kafka Event Listener cho Match Service theo Backend Instructions
 * Lắng nghe FindShipperEvent từ Delivery Service và delegate to MatchEventService
 * Clean separation: Listener chỉ handle Kafka, business logic ở Service layer
 */
@Slf4j
@Component
public class FindShipperEventListener {
    
    private final MatchService matchService;
    private final MatchEventService matchEventService;
    
    // ✅ Constructor Injection Pattern (MANDATORY)  
    public FindShipperEventListener(MatchService matchService, MatchEventService matchEventService) {
        this.matchService = matchService;
        this.matchEventService = matchEventService;
    }
    
    /**
     * ✅ Lắng nghe FindShipperEvent và tự động gọi findNearbyShippers
     */
    @KafkaListener(topics = KafkaTopicConstants.FIND_SHIPPER_TOPIC)
    public void handleFindShipperEvent(
            @Payload FindShipperEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) Long timestamp,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("📥 Received FindShipperEvent for delivery: {} from topic: {} partition: {} timestamp: {}",
                    event.getDeliveryId(), topic, partition, timestamp);
            
            // ✅ Validate event data
            if (event.getDeliveryId() == null) {
                log.error("💥 Invalid FindShipperEvent: deliveryId is null");
                acknowledgment.acknowledge();
                return;
            }
            
            // ✅ Convert event to FindNearbyShippersRequest
            FindNearbyShippersRequest request = createFindShippersRequest(event);
            
            // ✅ Gọi findNearbyShippers với system user context
            Long systemUserId = 1L; // System user ID
            String systemRole = "SYSTEM";
            
            matchService.findNearbyShippers(request, systemUserId, systemRole)
                    .subscribe(
                        shippers -> {
                            log.info("✅ Found {} nearby shippers for delivery: {}", 
                                   shippers.size(), event.getDeliveryId());
                            
                            // ✅ Delegate to MatchEventService for business logic
                            matchEventService.processShipperMatchResult(event, shippers);
                            
                            // ✅ Acknowledge after successful processing
                            acknowledgment.acknowledge();
                        },
                        error -> {
                            log.error("💥 Error finding shippers for delivery: {} - Error: {}", 
                                     event.getDeliveryId(), error.getMessage(), error);
                            
                            // ✅ Acknowledge even on error to avoid infinite retry
                            // TODO: Implement DLQ (Dead Letter Queue) for failed events
                            acknowledgment.acknowledge();
                        }
                    );
            
        } catch (Exception e) {
            log.error("🔥 Unexpected error processing FindShipperEvent for delivery: {} - Error: {}", 
                     event.getDeliveryId(), e.getMessage(), e);
            
            // ✅ Acknowledge to prevent blocking
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * ✅ Convert FindShipperEvent to FindNearbyShippersRequest với null safety
     */
    private FindNearbyShippersRequest createFindShippersRequest(FindShipperEvent event) {
        FindNearbyShippersRequest request = new FindNearbyShippersRequest();
        
        // ✅ Null safety check for pickup coordinates
        if (event.getPickupLat() != null && event.getPickupLng() != null) {
            // Sử dụng pickup location để tìm shipper gần restaurant
            request.setLatitude(event.getPickupLat());
            request.setLongitude(event.getPickupLng());
            
            log.debug("🎯 Using pickup location: {}, {} for delivery: {}", 
                     event.getPickupLat(), event.getPickupLng(), event.getDeliveryId());
        } 
        else if (event.getDeliveryLat() != null && event.getDeliveryLng() != null) {
            // Fallback: sử dụng delivery location nếu pickup location null
            request.setLatitude(event.getDeliveryLat());
            request.setLongitude(event.getDeliveryLng());
            
            log.warn("⚠️ Pickup location null, using delivery location: {}, {} for delivery: {}", 
                    event.getDeliveryLat(), event.getDeliveryLng(), event.getDeliveryId());
        } 
        else {
            // Default location (TP.HCM center) nếu cả 2 đều null
            request.setLatitude(10.762622); // Landmark 81 coordinates
            request.setLongitude(106.660172);
            
            log.error("🔥 Both pickup and delivery coordinates are null for delivery: {}, using default location", 
                     event.getDeliveryId());
        }
        
        // Default search parameters
        request.setRadiusKm(5.0); // 5km radius
        request.setMaxShippers(10); // Tối đa 10 shippers
        
        return request;
    }
}
