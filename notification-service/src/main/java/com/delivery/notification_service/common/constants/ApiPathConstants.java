package com.delivery.notification_service.common.constants;

/**
 * ✅ API Path Constants cho Notification Service theo Backend Instructions
 */
public class ApiPathConstants {
    
    // Base paths
    public static final String NOTIFICATIONS = "/api/notifications";
    
    // Notification endpoints
    public static final String SEND_NOTIFICATION = "/send";
    public static final String USER_NOTIFICATIONS = "/user/{userId}";
    public static final String MARK_AS_READ = "/{id}/read";
    public static final String MARK_ALL_AS_READ = "/mark-all-read";
    
    private ApiPathConstants() {
        // Utility class
    }
}
