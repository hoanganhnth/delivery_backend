package com.delivery.notification_service.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * ✅ Notification Response DTO theo Backend Instructions
 */
@Getter
@Setter
public class NotificationResponse {

    private Long id;
    private Long userId;
    private String title;
    private String message;
    private String type;
    private String priority;
    private String status;
    private Boolean isRead;
    private Long relatedEntityId;
    private String relatedEntityType;
    private String data;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
