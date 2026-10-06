package com.delivery.notification.domain;

/**
 * ✅ Notification Constants cho Notification Service theo Backend Instructions
 */
final class Types {
    
    // Notification Types
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String DELIVERY_PENDING = "DELIVERY_PENDING";
    public static final String DELIVERY_FINDING_SHIPPER = "DELIVERY_FINDING_SHIPPER";
    public static final String DELIVERY_WAIT_SHIPPER_CONFIRM = "DELIVERY_WAIT_SHIPPER_CONFIRM";
    public static final String DELIVERY_SHIPPER_NOT_FOUND = "DELIVERY_SHIPPER_NOT_FOUND";
    public static final String DELIVERY_ASSIGNED = "DELIVERY_ASSIGNED";
    public static final String DELIVERY_PICKED_UP = "DELIVERY_PICKED_UP";
    public static final String DELIVERY_DELIVERING = "DELIVERY_DELIVERING";
    public static final String DELIVERY_DELIVERED = "DELIVERY_DELIVERED";
    public static final String DELIVERY_CANCELLED = "DELIVERY_CANCELLED";
    
    // Match/Shipper notifications 
    public static final String MATCH_FOUND = "MATCH_FOUND";
    
    // Notification Priority
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_MEDIUM = "MEDIUM";
    
    private Types() {}
}
