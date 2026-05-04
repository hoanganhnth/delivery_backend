package com.delivery.delivery_service.common.constants;

/**
 * ✅ Kafka Topic Constants theo Backend Instructions
 */
public class KafkaTopicConstants {
    
    // ✅ Incoming topics from other services
    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_UPDATED_TOPIC = "order.updated";
    public static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    public static final String SHIPPER_NOT_FOUND_TOPIC = "shipper.not-found";
    public static final String SHIPPER_FOUND_TOPIC = "shipper.found";
    
    // ✅ Outgoing topics to other services
    public static final String FIND_SHIPPER_TOPIC = "delivery.find-shipper";
    public static final String DELIVERY_STATUS_UPDATED_TOPIC = "delivery.status-updated";
    public static final String SHIPPER_ACCEPTED_TOPIC = "delivery.shipper-accepted";
    public static final String DELIVERY_CANCELLED_TOPIC = "delivery.cancelled";
    public static final String DELIVERY_COMPLETED_TOPIC = "delivery.completed";
    public static final String DELIVERY_PICKED_UP_TOPIC = "delivery.picked-up";
    
    // ✅ NEW: Thay thế REST call đến tracking-service
    public static final String SHIPPER_STATUS_CHANGE_TOPIC = "shipper.status-change";

    // ✅ NEW: Shipper rejected event — triggers re-assignment flow
    public static final String SHIPPER_REJECTED_TOPIC = "delivery.shipper-rejected";
    
    private KafkaTopicConstants() {
        // Utility class
    }
}
