package com.delivery.delivery_service.controller;

import com.delivery.delivery_service.dto.response.DeliveryResponse;
import com.delivery.delivery_service.service.DeliveryService;
import com.delivery.delivery_service.service.DeliveryWebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * ✅ WebSocket Controller cho delivery tracking theo Backend Instructions
 */
@Slf4j
@Controller
public class DeliveryWebSocketController {
    
    private final DeliveryService deliveryService;
    private final DeliveryWebSocketService webSocketService;
    
    // ✅ Constructor Injection Pattern (MANDATORY)
    public DeliveryWebSocketController(DeliveryService deliveryService,
                                     DeliveryWebSocketService webSocketService) {
        this.deliveryService = deliveryService;
        this.webSocketService = webSocketService;
    }
    
    /**
     * Handle client subscription cho delivery tracking
     */
    @SubscribeMapping("/delivery/{deliveryId}/track")
    public DeliveryResponse handleDeliveryTrackingSubscription(
            @DestinationVariable Long deliveryId,
            Principal principal) {
        
        try {
            log.info("📡 Client subscribed to delivery {} tracking", deliveryId);
            
            // Return current delivery status immediately upon subscription
            // TODO: Get userId from Principal/JWT token
            Long userId = 1L; // Temporary - should extract from authentication
            String role = "USER"; // Temporary - should extract from authentication
            
            return deliveryService.getDeliveryById(deliveryId, userId, role);
            
        } catch (Exception e) {
            log.error("💥 Error handling delivery tracking subscription: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Handle delivery status inquiries từ client
     */
    @MessageMapping("/delivery/{deliveryId}/status")
    @SendTo("/topic/delivery/{deliveryId}/status")
    public DeliveryResponse handleStatusInquiry(
            @DestinationVariable Long deliveryId,
            Principal principal) {
        
        try {
            log.info("🔍 Status inquiry for delivery {}", deliveryId);
            
            // TODO: Get userId from Principal/JWT token
            Long userId = 1L; // Temporary 
            String role = "USER"; // Temporary
            
            return deliveryService.getDeliveryById(deliveryId, userId, role);
            
        } catch (Exception e) {
            log.error("💥 Error handling status inquiry: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Handle ping/heartbeat từ client
     */
    @MessageMapping("/delivery/ping")
    @SendTo("/topic/delivery/pong")
    public String handlePing(@Payload String message) {
        log.debug("🏓 Received ping: {}", message);
        return "pong-" + System.currentTimeMillis();
    }
}
