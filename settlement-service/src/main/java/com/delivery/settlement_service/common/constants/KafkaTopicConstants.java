package com.delivery.settlement_service.common.constants;

public class KafkaTopicConstants {
    public static final String DELIVERY_COMPLETED_TOPIC = "delivery.completed";
    public static final String DELIVERY_PICKED_UP_TOPIC = "delivery.picked-up";
    
    private KafkaTopicConstants() {
        // Utility class
    }
}
