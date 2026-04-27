package com.delivery.match_service.common.constants;

/**
 * ✅ Kafka Topic Constants cho Match Service
 * Centralized topic names theo Backend Instructions
 */
public class KafkaTopicConstants {
    
    // Topics match-service listens to
    public static final String FIND_SHIPPER_TOPIC = "delivery.find-shipper";
    public static final String DELIVERY_CANCELLED_TOPIC = "delivery.cancelled";
    
    // Topics match-service publishes to  
    public static final String SHIPPER_NOT_FOUND_TOPIC = "shipper.not-found";
    public static final String SHIPPER_FOUND_TOPIC = "shipper.found";
    public static final String SHIPPER_MATCHED_TOPIC = "shipper.matched";
    public static final String NO_SHIPPER_AVAILABLE_TOPIC = "no.shipper.available";

    // ✅ NEW: Topics for event-driven shipper tracking (replaces REST calls)
    public static final String SHIPPER_LOCATION_UPDATED_TOPIC = "shipper.location-updated";
    public static final String SHIPPER_STATUS_CHANGE_TOPIC = "shipper.status-change";
    
    private KafkaTopicConstants() {
        // Utility class
    }
}
