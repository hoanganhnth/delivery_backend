package com.delivery.match_service.common.constants;

/**
 * ✅ Kafka Topic Constants cho Match Service
 * Centralized topic names theo Backend Instructions
 */
public class KafkaTopicConstants {
    
    // Topics match-service publishes to  
    public static final String SHIPPER_NOT_FOUND_TOPIC = "shipper.not-found";
    public static final String SHIPPER_FOUND_TOPIC = "shipper.found";
    private KafkaTopicConstants() {
        // Utility class
    }
}
