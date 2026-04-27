package com.delivery.tracking_service.common.constants;

/**
 * ✅ Kafka Topic Constants cho Tracking Service
 */
public class KafkaTopicConstants {

    // Topic tracking-service publishes to
    public static final String SHIPPER_LOCATION_UPDATED_TOPIC = "shipper.location-updated";

    // Topic tracking-service listens to
    public static final String SHIPPER_STATUS_CHANGE_TOPIC = "shipper.status-change";

    private KafkaTopicConstants() {
        // Utility class
    }
}
