package com.delivery.order_service.common.constants;

/**
 * ✅ Kafka Topic Constants cho Order Service theo AI Coding Instructions
 */
public class KafkaTopicConstants {
    
    // ✅ Order-related topics (Order Service publishes)
    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_UPDATED_TOPIC = "order.updated";
    public static final String ORDER_STATUS_UPDATED_TOPIC = "order.status-updated";
    public static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    
    // ✅ Topics mà Order Service consume từ services khác
    public static final String DELIVERY_STATUS_UPDATED_TOPIC = "delivery.status-updated";
    public static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    public static final String PAYMENT_FAILED_TOPIC = "payment.failed";
    public static final String RESTAURANT_CONFIRMED_TOPIC = "restaurant.order-confirmed";
    public static final String RESTAURANT_REJECTED_TOPIC = "restaurant.order-rejected";
    
    // ✅ Shipper-related topics
    public static final String SHIPPER_ACCEPTED_TOPIC = "delivery.shipper-accepted";
    public static final String SHIPPER_REJECTED_TOPIC = "delivery.shipper-rejected";

    private KafkaTopicConstants() {
        // Utility class - prevent instantiation
    }
}
