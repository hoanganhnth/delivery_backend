package com.delivery.tracking_service.common.constants;

/**
 * ✅ Kafka Topic Constants cho Tracking Service
 */
public class KafkaTopicConstants {

    // Topic tracking-service publishes to
    public static final String SHIPPER_LOCATION_UPDATED_TOPIC = "shipper.location-updated";

    private KafkaTopicConstants() {
        // Utility class
    }
}
