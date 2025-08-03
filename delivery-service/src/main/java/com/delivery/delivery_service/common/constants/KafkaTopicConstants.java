package com.delivery.delivery_service.common.constants;

/**
 * ✅ Kafka Topic Constants theo Backend Instructions
 */
public class KafkaTopicConstants {
    
    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_UPDATED_TOPIC = "order.updated";
    public static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    
    private KafkaTopicConstants() {
        // Utility class
    }
}
