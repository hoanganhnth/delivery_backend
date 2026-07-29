package com.delivery.notification_service.common.constants;

/**
 * ✅ Notification Constants cho Notification Service theo Backend Instructions
 */
public class NotificationConstants {
    
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
    
    // Notification Status
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    
    // Redis Keys
    public static final String REDIS_FCM_TOKENS = "fcm:tokens:";
    public static final String REDIS_FCM_TOKEN_OWNER = "fcm:token-owner:";
    
    private NotificationConstants() {
        // Utility class
    }
}
