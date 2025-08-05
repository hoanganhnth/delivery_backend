package com.delivery.notification_service.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ✅ WebSocket Message DTO cho real-time notifications theo Backend Instructions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {

    private String type; // NOTIFICATION, TYPING, STATUS_UPDATE
    private Long userId;
    private String title;
    private String message;
    private String notificationType; // ORDER_CREATED, ORDER_DELIVERED, etc.
    private String priority;
    private Long relatedEntityId;
    private String relatedEntityType;
    private String data;
    private LocalDateTime timestamp;

    public WebSocketMessage(String type, Long userId, String message) {
        this.type = type;
        this.userId = userId;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
