package com.delivery.delivery_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * ✅ WebSocket Configuration cho Delivery Service theo Backend Instructions
 * Support real-time delivery tracking updates
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker để carry messages back to client
        config.enableSimpleBroker("/topic", "/queue");
        
        // Destination prefix cho messages from client to server
        config.setApplicationDestinationPrefixes("/app");
        
        // User-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoints cho client connections
        registry.addEndpoint("/ws/delivery")
                .setAllowedOriginPatterns("*") // Allow all origins for development
                .withSockJS(); // Enable SockJS fallback options
        
        // Alternative endpoint without SockJS
        registry.addEndpoint("/ws/delivery-native")
                .setAllowedOriginPatterns("*");
    }
}
