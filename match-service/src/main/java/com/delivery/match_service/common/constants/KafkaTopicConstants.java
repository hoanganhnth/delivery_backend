package com.delivery.match_service.common.constants;

/**
 * ✅ Kafka Topic Constants cho Match Service theo Backend Instructions
 */
public class KafkaTopicConstants {
    
    // ✅ Incoming topics từ other services
    public static final String FIND_SHIPPER_TOPIC = "delivery.find-shipper";
    
    // ✅ Outgoing topics - aligned with Notification Service expectations
    public static final String SHIPPER_MATCHED_TOPIC = "match.shipper-matched";        // When shipper found
    public static final String SHIPPER_REQUEST_TOPIC = "match.shipper-request";        // Request to shipper
    public static final String SHIPPER_ACCEPTED_TOPIC = "match.shipper-accepted";      // Shipper accepted order
    public static final String SHIPPER_REJECTED_TOPIC = "match.shipper-rejected";      // Shipper rejected order
    public static final String NO_SHIPPER_AVAILABLE_TOPIC = "match.no-shipper-available"; // No shipper found
    
    private KafkaTopicConstants() {
        // Utility class
    }
}
