package com.delivery.match_service.common.constants;

/**
 * ✅ Kafka Topic Constants cho Match Service theo Backend Instructions
 */
public class KafkaTopicConstants {
    
    // ✅ Incoming topics từ other services
    public static final String FIND_SHIPPER_TOPIC = "delivery.find-shipper";
    
    // ✅ Outgoing topics (có thể thêm sau)
    public static final String SHIPPER_MATCHED_TOPIC = "match.shipper-matched";
    public static final String NO_SHIPPER_AVAILABLE_TOPIC = "match.no-shipper-available";
    
    private KafkaTopicConstants() {
        // Utility class
    }
}
