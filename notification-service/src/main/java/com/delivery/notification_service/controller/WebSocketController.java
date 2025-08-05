package com.delivery.notification_service.controller;

import com.delivery.notification_service.dto.websocket.WebSocketMessage;
import com.delivery.notification_service.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * ✅ WebSocket Controller cho real-time notifications theo Backend Instructions
 */
@Slf4j
@Controller
public class WebSocketController {

    private final RedisService redisService;

    public WebSocketController(RedisService redisService) {
        this.redisService = redisService;
    }

    @MessageMapping("/notification/connect/{userId}")
    public void connectUser(@DestinationVariable Long userId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        redisService.storeUserSession(userId, sessionId);
        
        log.info("🔌 User {} connected with session {}", userId, sessionId);
    }

    @MessageMapping("/notification/disconnect/{userId}")
    public void disconnectUser(@DestinationVariable Long userId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        redisService.removeUserSession(userId, sessionId);
        
        log.info("🔌 User {} disconnected from session {}", userId, sessionId);
    }

    @MessageMapping("/notification/typing/{userId}")
    public void handleTyping(@DestinationVariable Long userId, @Payload WebSocketMessage message) {
        log.debug("⌨️ User {} is typing", userId);
        // Handle typing indicator logic if needed
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("🔌 New WebSocket connection: {}", sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // Remove session from all users (we don't know which user it belonged to)
        // In a real implementation, you might want to store session->user mapping
        log.info("🔌 WebSocket disconnection: {}", sessionId);
    }
}
