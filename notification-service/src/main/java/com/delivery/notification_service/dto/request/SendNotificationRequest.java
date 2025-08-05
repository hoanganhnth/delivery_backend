package com.delivery.notification_service.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * ✅ Send Notification Request DTO theo Backend Instructions
 */
@Getter
@Setter
public class SendNotificationRequest {

    private Long userId;
    private String title;
    private String message;
    private String type;
    private String priority = "MEDIUM";
    private Long relatedEntityId;
    private String relatedEntityType;
    private String data; // JSON string for additional data
    private Boolean sendPush = true;
    private Boolean sendWebSocket = true;
}
