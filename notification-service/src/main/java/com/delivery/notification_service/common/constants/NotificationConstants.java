package com.delivery.notification_service.common.constants;

/**
 * ✅ Notification Constants cho Notification Service theo Backend Instructions
 */
public class NotificationConstants {
    
    // Notification Types
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_PREPARING = "ORDER_PREPARING";
    public static final String ORDER_READY = "ORDER_READY";
    public static final String ORDER_PICKED_UP = "ORDER_PICKED_UP";
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String SHIPPER_ASSIGNED = "SHIPPER_ASSIGNED";
    public static final String DELIVERY_STARTED = "DELIVERY_STARTED";
    public static final String DELIVERY_COMPLETED = "DELIVERY_COMPLETED";
    
    // Match/Shipper notifications 
    public static final String MATCH_FOUND = "MATCH_FOUND";
    public static final String DELIVERY_REQUEST = "DELIVERY_REQUEST";
    public static final String SHIPPER_ACCEPTED = "SHIPPER_ACCEPTED";
    public static final String SHIPPER_CONFIRMED = "SHIPPER_CONFIRMED";
    public static final String SHIPPER_REJECTED = "SHIPPER_REJECTED";
    
    // Notification Priority
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_MEDIUM = "MEDIUM";
    public static final String PRIORITY_LOW = "LOW";
    
    // Notification Status
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_READ = "READ";
    
    // Redis Keys
    public static final String REDIS_USER_SESSIONS = "user:sessions:";
    public static final String REDIS_NOTIFICATION_CACHE = "notification:cache:";
    public static final String REDIS_FCM_TOKENS = "fcm:tokens:";
    
    private NotificationConstants() {
        // Utility class
    }
}
