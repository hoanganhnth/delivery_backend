package com.delivery.delivery_service.common.constants;

/**
 * ✅ Kafka Topic Constants theo Backend Instructions
 */
public class KafkaTopicConstants {
    
    // ✅ Incoming topics from other services
    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_UPDATED_TOPIC = "order.updated";
    public static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    
    // ✅ Outgoing topics to other services
    public static final String FIND_SHIPPER_TOPIC = "delivery.find-shipper";
    public static final String DELIVERY_STATUS_UPDATED_TOPIC = "delivery.status-updated";
    public static final String SHIPPER_ACCEPTED_TOPIC = "delivery.shipper-accepted";
    
    private KafkaTopicConstants() {
        // Utility class
    }
}
