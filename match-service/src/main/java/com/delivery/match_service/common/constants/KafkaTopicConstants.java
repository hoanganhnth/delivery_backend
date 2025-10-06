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
    public static final String SHIPPER_MATCHED_TOPIC = "shipper.matched";
    public static final String NO_SHIPPER_AVAILABLE_TOPIC = "no.shipper.available";
    
    private KafkaTopicConstants() {
        // Utility class
    }
}
