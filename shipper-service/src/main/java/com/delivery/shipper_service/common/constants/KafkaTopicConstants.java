package com.delivery.shipper_service.common.constants;

/**
 * ✅ Kafka Topic Constants cho Shipper Service
 */
public class KafkaTopicConstants {
    
    // ✅ Incoming topics from other services
    public static final String DELIVERY_COMPLETED_TOPIC = "delivery.completed";
    
    private KafkaTopicConstants() {
        // Utility class
    }
}
