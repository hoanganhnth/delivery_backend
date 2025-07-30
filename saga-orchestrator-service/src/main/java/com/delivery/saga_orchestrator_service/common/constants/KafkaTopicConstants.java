package com.delivery.saga_orchestrator_service.common.constants;

public class KafkaTopicConstants {
    
    // Delivery Service Topics
    public static final String DELIVERY_ASSIGNMENT_REQUEST = "delivery.assignment.request";
    public static final String DELIVERY_ASSIGNMENT_RESPONSE = "delivery.assignment.response";
    public static final String DELIVERY_STATUS_UPDATE = "delivery.status.update";
    public static final String DELIVERY_LOCATION_UPDATE = "delivery.location.update";
    
    // Shipper Service Topics
    public static final String SHIPPER_SEARCH_REQUEST = "shipper.search.request";
    public static final String SHIPPER_SEARCH_RESPONSE = "shipper.search.response";
    public static final String SHIPPER_NOTIFICATION = "shipper.notification";
    
    // Order Service Topics
    public static final String ORDER_STATUS_UPDATE = "order.status.update";
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    
    private KafkaTopicConstants() {
        // Private constructor to prevent instantiation
    }
}
