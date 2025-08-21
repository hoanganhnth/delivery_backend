package com.delivery.notification_service.service;

import com.delivery.notification_service.dto.websocket.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * ✅ WebSocket Service cho real-time notifications theo Backend Instructions
 */
@Slf4j
@Service
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisService redisService;

    public WebSocketService(SimpMessagingTemplate messagingTemplate, RedisService redisService) {
        this.messagingTemplate = messagingTemplate;
        this.redisService = redisService;
    }

    /**
     * Send notification to specific user via WebSocket
     */
    public void sendNotificationToUser(Long userId, WebSocketMessage message) {
        try {
            // Check if user is online
            // if (!redisService.isUserOnline(userId)) {
            //     log.debug("📴 User {} is offline, skipping WebSocket notification", userId);
            //     return;
            // }

            String destination = "/topic/user/" + userId;
            messagingTemplate.convertAndSend(destination, message);
            
            log.info("📡 Sent WebSocket notification to user {}: {}", userId, message.getTitle());
            
        } catch (Exception e) {
            log.error("💥 Failed to send WebSocket notification to user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Send notification to all connected users
     */
    public void sendNotificationToAll(WebSocketMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/notifications", message);
            log.info("📢 Sent broadcast WebSocket notification: {}", message.getTitle());
            
        } catch (Exception e) {
            log.error("💥 Failed to send broadcast WebSocket notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Send typing indicator to user
     */
    public void sendTypingIndicator(Long userId, String typingUser) {
        try {
            WebSocketMessage message = new WebSocketMessage("TYPING", userId, typingUser + " is typing...");
            String destination = "/topic/user/" + userId;
            messagingTemplate.convertAndSend(destination, message);
            
            log.debug("⌨️ Sent typing indicator to user {}", userId);
            
        } catch (Exception e) {
            log.error("💥 Failed to send typing indicator: {}", e.getMessage(), e);
        }
    }

    /**
     * Send status update to user
     */
    public void sendStatusUpdate(Long userId, String status, String message) {
        try {
            WebSocketMessage wsMessage = new WebSocketMessage("STATUS_UPDATE", userId, message);
            String destination = "/topic/user/" + userId;
            messagingTemplate.convertAndSend(destination, wsMessage);
            
            log.debug("📊 Sent status update to user {}: {}", userId, status);
            
        } catch (Exception e) {
            log.error("💥 Failed to send status update: {}", e.getMessage(), e);
        }
    }
}
