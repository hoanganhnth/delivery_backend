package com.delivery.notification_service.common.constants;

/**
 * ✅ Kafka Topic Constants cho Notification Service theo Backend Instructions
 */
public class KafkaTopicConstants {
    
    // Input topics (consuming from other services)
    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_STATUS_UPDATED_TOPIC = "order.status-updated";
    public static final String DELIVERY_STATUS_UPDATED_TOPIC = "delivery.status-updated";
    public static final String SHIPPER_ASSIGNED_TOPIC = "delivery.shipper-assigned";
    
    // Match Service topics
    public static final String MATCH_FOUND_TOPIC = "match.shipper-found";
    public static final String MATCH_REQUEST_TOPIC = "match.shipper-request";
    public static final String MATCH_ACCEPTED_TOPIC = "match.shipper-accepted";
    public static final String MATCH_REJECTED_TOPIC = "match.shipper-rejected";
    
    // Output topics (producing notifications)
    public static final String NOTIFICATION_CREATED_TOPIC = "notification.created";
    public static final String NOTIFICATION_SENT_TOPIC = "notification.sent";
    
    private KafkaTopicConstants() {
        // Utility class
    }
}
