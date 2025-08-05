package com.delivery.notification_service.common.constants;

/**
 * ✅ API Path Constants cho Notification Service theo Backend Instructions
 */
public class ApiPathConstants {
    
    // Base paths
    public static final String NOTIFICATIONS = "/api/notifications";
    public static final String WEBSOCKET = "/ws";
    
    // Notification endpoints
    public static final String SEND_NOTIFICATION = "/send";
    public static final String USER_NOTIFICATIONS = "/user/{userId}";
    public static final String MARK_AS_READ = "/{id}/read";
    public static final String MARK_ALL_AS_READ = "/mark-all-read";
    
    // WebSocket endpoints
    public static final String WS_NOTIFICATION = "/notification";
    public static final String WS_USER_TOPIC = "/topic/user/";
    
    private ApiPathConstants() {
        // Utility class
    }
}
