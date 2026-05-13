package com.delivery.analytics_service.common;

/**
 * Kafka Topic Constants — liệt kê tất cả topics mà Analytics Service lắng nghe
 */
public class KafkaTopicConstants {

    // Order events
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_STATUS_UPDATED = "order.status-updated";
    public static final String ORDER_CANCELLED = "order.cancelled";

    // Payment/Settlement events
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";

    // Delivery events
    public static final String DELIVERY_STATUS_UPDATED = "delivery.status-updated";

    private KafkaTopicConstants() {}
}
