package com.delivery.livestream_service.common.constants;

public class KafkaTopicConstants {
    
    // Livestream topics
    public static final String LIVESTREAM_STARTED_TOPIC = "livestream.started";
    public static final String LIVESTREAM_ENDED_TOPIC = "livestream.ended";
    public static final String PRODUCT_PINNED_TOPIC = "livestream.product.pinned";
    public static final String PRODUCT_UNPINNED_TOPIC = "livestream.product.unpinned";
    
    private KafkaTopicConstants() {
        // Prevent instantiation
    }
}
