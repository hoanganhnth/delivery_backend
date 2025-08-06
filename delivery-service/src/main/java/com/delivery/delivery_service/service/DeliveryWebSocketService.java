package com.delivery.delivery_service.service;

import com.delivery.delivery_service.dto.response.DeliveryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * ✅ WebSocket Service cho real-time delivery tracking theo Backend Instructions
 */
@Slf4j
@Service
public class DeliveryWebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    // ✅ Constructor Injection Pattern (MANDATORY)
    public DeliveryWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Gửi delivery status update cho customer
     */
    public void sendDeliveryUpdateToCustomer(Long userId, DeliveryResponse delivery) {
        try {
            String destination = "/topic/customer/" + userId + "/delivery";
            
            log.info("📡 Sending delivery update to customer {} via WebSocket: {}", 
                    userId, delivery.getStatus());
            
            messagingTemplate.convertAndSend(destination, delivery);
            
        } catch (Exception e) {
            log.error("💥 Failed to send WebSocket message to customer {}: {}", 
                    userId, e.getMessage(), e);
        }
    }
    
    /**
     * Gửi delivery update cho shipper
     */
    public void sendDeliveryUpdateToShipper(Long shipperId, DeliveryResponse delivery) {
        try {
            String destination = "/topic/shipper/" + shipperId + "/delivery";
            
            log.info("📡 Sending delivery update to shipper {} via WebSocket: {}", 
                    shipperId, delivery.getStatus());
            
            messagingTemplate.convertAndSend(destination, delivery);
            
        } catch (Exception e) {
            log.error("💥 Failed to send WebSocket message to shipper {}: {}", 
                    shipperId, e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast delivery update cho all tracking users (admin, restaurant)
     */
    public void broadcastDeliveryUpdate(DeliveryResponse delivery) {
        try {
            String destination = "/topic/delivery/" + delivery.getId() + "/updates";
            
            log.info("📡 Broadcasting delivery {} update to all subscribers: {}", 
                    delivery.getId(), delivery.getStatus());
            
            messagingTemplate.convertAndSend(destination, delivery);
            
        } catch (Exception e) {
            log.error("💥 Failed to broadcast delivery update for {}: {}", 
                    delivery.getId(), e.getMessage(), e);
        }
    }
}
