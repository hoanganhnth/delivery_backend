package com.delivery.match_service.service.impl;

import com.delivery.match_service.common.constants.KafkaTopicConstants;
import com.delivery.match_service.dto.event.FindShipperEvent;
import com.delivery.match_service.dto.event.MatchEvent;
import com.delivery.match_service.dto.response.NearbyShipperResponse;
import com.delivery.match_service.service.MatchEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * ✅ Match Event Service Implementation theo Backend Instructions
 * Handles event publishing with proper validation và business logic
 */
@Slf4j
@Service
public class MatchEventServiceImpl implements MatchEventService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // ✅ Constructor Injection Pattern (MANDATORY)
    public MatchEventServiceImpl(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    @Override
    public void processShipperMatchResult(FindShipperEvent deliveryEvent, List<NearbyShipperResponse> shippers) {
        if (shippers == null || shippers.isEmpty()) {
            log.warn("⚠️ No shippers found for delivery: {} at location: {}, {}", 
                    deliveryEvent.getDeliveryId(), deliveryEvent.getPickupLat(), deliveryEvent.getPickupLng());
            
            publishNoShipperAvailableEvent(deliveryEvent);
            return;
        }
        
        // Log found shippers
        shippers.forEach(shipper -> 
            log.info("🚚 Found shipper at location: {}, {} with distance: {}km for delivery: {}", 
                    shipper.getLatitude(), shipper.getLongitude(), shipper.getDistanceKm(), deliveryEvent.getDeliveryId())
        );
        
        // ✅ Select best shipper using business logic
        NearbyShipperResponse bestShipper = selectBestShipper(shippers);
        
        if (bestShipper == null) {
            log.error("💥 Failed to select valid shipper for delivery: {}", deliveryEvent.getDeliveryId());
            publishNoShipperAvailableEvent(deliveryEvent);
            return;
        }
        
        Long shipperId = bestShipper.getShipperId();
        log.info("🎯 Best shipper selected: shipperId={} at location: {}, {} for delivery: {}", 
                shipperId, bestShipper.getLatitude(), bestShipper.getLongitude(), deliveryEvent.getDeliveryId());
        
        // ✅ Publish MatchFoundEvent
        publishMatchFoundEvent(deliveryEvent, bestShipper);
    }
    
    @Override
    public void publishMatchFoundEvent(FindShipperEvent deliveryEvent, NearbyShipperResponse shipper) {
        try {
            // ✅ Validate data before creating event
            if (!isValidShipperInfo(shipper)) {
                log.error("💥 Invalid shipper information for delivery: {}", deliveryEvent.getDeliveryId());
                publishNoShipperAvailableEvent(deliveryEvent);
                return;
            }
            
            if (!isValidDeliveryEvent(deliveryEvent)) {
                log.error("💥 Invalid delivery event information for delivery: {}", deliveryEvent.getDeliveryId());
                return;
            }
            
            // ✅ Create MatchEvent với validated data
            MatchEvent matchEvent = createMatchFoundEvent(deliveryEvent, shipper);
            
            // ✅ Publish to Notification Service
            kafkaTemplate.send(KafkaTopicConstants.SHIPPER_MATCHED_TOPIC, matchEvent);
            
            log.info("✅ Published MatchFoundEvent to topic '{}' for match: {}, delivery: {}, shipper: {}", 
                    KafkaTopicConstants.SHIPPER_MATCHED_TOPIC, matchEvent.getMatchId(), 
                    deliveryEvent.getDeliveryId(), shipper.getShipperId());
                    
        } catch (Exception e) {
            log.error("💥 Failed to publish MatchFoundEvent for delivery: {} - Error: {}", 
                     deliveryEvent.getDeliveryId(), e.getMessage(), e);
        }
    }
    
    @Override
    public void publishNoShipperAvailableEvent(FindShipperEvent deliveryEvent) {
        try {
            log.warn("📤 Should publish NoShipperAvailableEvent for delivery: {} (implementing...)", 
                    deliveryEvent.getDeliveryId());
            
            // ✅ For now, publish to no-shipper-available topic
            kafkaTemplate.send(KafkaTopicConstants.NO_SHIPPER_AVAILABLE_TOPIC, deliveryEvent);
            
            log.info("✅ Published NoShipperAvailableEvent for delivery: {}", deliveryEvent.getDeliveryId());
            
        } catch (Exception e) {
            log.error("💥 Failed to publish NoShipperAvailableEvent for delivery: {} - Error: {}", 
                     deliveryEvent.getDeliveryId(), e.getMessage(), e);
        }
    }
    
    @Override
    public NearbyShipperResponse selectBestShipper(List<NearbyShipperResponse> shippers) {
        if (shippers == null || shippers.isEmpty()) {
            return null;
        }
        
        // ✅ Filter valid shippers first
        List<NearbyShipperResponse> validShippers = shippers.stream()
                .filter(this::isValidShipperInfo)
                .toList();
        
        if (validShippers.isEmpty()) {
            log.warn("⚠️ No valid shippers found after filtering");
            return null;
        }
        
        // ✅ Simple selection logic: closest shipper (first in sorted list)
        NearbyShipperResponse bestShipper = validShippers.get(0);
        
        log.debug("🎯 Selected shipper {} with distance {}km", 
                bestShipper.getShipperId(), bestShipper.getDistanceKm());
        
        return bestShipper;
    }
    
    /**
     * ✅ Create MatchEvent từ delivery event và selected shipper
     */
    private MatchEvent createMatchFoundEvent(FindShipperEvent deliveryEvent, NearbyShipperResponse shipper) {
        MatchEvent matchEvent = MatchEvent.createMatchFoundEvent(
                deliveryEvent.getOrderId(),
                deliveryEvent.getDeliveryId(),
                shipper.getShipperId(),
                shipper.getShipperName(),
                shipper.getShipperPhone(),
                shipper.getDistanceKm(),
                deliveryEvent.getRestaurantName(),
                deliveryEvent.getPickupAddress(),
                deliveryEvent.getDeliveryAddress(),
                calculateEstimatedPrice(shipper.getDistanceKm()),
                calculateEstimatedTime(shipper.getDistanceKm())
        );
        
        // Set coordinates
        matchEvent.setPickupLat(deliveryEvent.getPickupLat());
        matchEvent.setPickupLng(deliveryEvent.getPickupLng());
        matchEvent.setDeliveryLat(deliveryEvent.getDeliveryLat());
        matchEvent.setDeliveryLng(deliveryEvent.getDeliveryLng());
        
        return matchEvent;
    }
    
    /**
     * ✅ Validate shipper information
     */
    private boolean isValidShipperInfo(NearbyShipperResponse shipper) {
        if (shipper == null) {
            log.error("🚫 Shipper response is null");
            return false;
        }
        
        if (shipper.getShipperId() == null || shipper.getShipperId() <= 0) {
            log.error("🚫 Invalid shipper ID: {}", shipper.getShipperId());
            return false;
        }
        
        if (shipper.getShipperName() == null || shipper.getShipperName().trim().isEmpty()) {
            log.warn("⚠️ Shipper name is null/empty for shipper: {}", shipper.getShipperId());
            return false;
        }
        
        if (shipper.getShipperPhone() == null || shipper.getShipperPhone().trim().isEmpty()) {
            log.warn("⚠️ Shipper phone is null/empty for shipper: {}", shipper.getShipperId());
            return false;
        }
        
        if (shipper.getDistanceKm() < 0) {
            log.error("🚫 Invalid distance: {} for shipper: {}", shipper.getDistanceKm(), shipper.getShipperId());
            return false;
        }
        
        return true;
    }
    
    /**
     * ✅ Validate delivery event information
     */
    private boolean isValidDeliveryEvent(FindShipperEvent event) {
        if (event == null) {
            log.error("🚫 Delivery event is null");
            return false;
        }
        
        if (event.getOrderId() == null || event.getOrderId() <= 0) {
            log.error("🚫 Invalid order ID: {}", event.getOrderId());
            return false;
        }
        
        if (event.getDeliveryId() == null || event.getDeliveryId() <= 0) {
            log.error("🚫 Invalid delivery ID: {}", event.getDeliveryId());
            return false;
        }
        
        if (event.getRestaurantName() == null || event.getRestaurantName().trim().isEmpty()) {
            log.warn("⚠️ Restaurant name is null/empty for delivery: {}", event.getDeliveryId());
            return false;
        }
        
        if (event.getPickupAddress() == null || event.getPickupAddress().trim().isEmpty()) {
            log.error("🚫 Pickup address is null/empty for delivery: {}", event.getDeliveryId());
            return false;
        }
        
        if (event.getDeliveryAddress() == null || event.getDeliveryAddress().trim().isEmpty()) {
            log.error("🚫 Delivery address is null/empty for delivery: {}", event.getDeliveryId());
            return false;
        }
        
        return true;
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
