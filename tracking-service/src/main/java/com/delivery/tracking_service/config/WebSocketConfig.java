package com.delivery.tracking_service.config;

import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ShipperLocationWebSocketHandler shipperLocationHandler;

    public WebSocketConfig(ShipperLocationWebSocketHandler shipperLocationHandler) {
        this.shipperLocationHandler = shipperLocationHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Endpoint để client theo dõi vị trí shipper theo thời gian thực
        registry.addHandler(shipperLocationHandler, "/ws/shipper-locations")
                .setAllowedOrigins("*"); // Cho phép tất cả origin (development)
    }
}
