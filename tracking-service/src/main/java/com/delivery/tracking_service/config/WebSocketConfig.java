package com.delivery.tracking_service.config;

import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import com.delivery.observability.CorrelationId;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ShipperLocationWebSocketHandler shipperLocationHandler;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            ShipperLocationWebSocketHandler shipperLocationHandler,
            @Value("${app.websocket.allowed-origins:http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173,http://127.0.0.1:3000}")
            String allowedOrigins) {
        this.shipperLocationHandler = shipperLocationHandler;
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Endpoint để client theo dõi vị trí shipper theo thời gian thực
        registry.addHandler(shipperLocationHandler, "/ws/shipper-locations")
                .addInterceptors(identityHeadersInterceptor())
                .setAllowedOrigins(allowedOrigins);
    }

    HandshakeInterceptor identityHeadersInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
                HttpHeaders headers = request.getHeaders();
                String userId = headers.getFirst("X-User-Id");
                String role = headers.getFirst("X-Role");
                if (userId == null || !userId.matches("[0-9]+") || role == null || role.isBlank()) {
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                }
                attributes.put("authenticatedUserId", Long.parseLong(userId));
                attributes.put("authenticatedRole", role);
                try {
                    attributes.put("correlationId",
                            CorrelationId.requireValidOrCreate(headers.getFirst(CorrelationId.HEADER)));
                } catch (IllegalArgumentException invalidCorrelationId) {
                    response.setStatusCode(HttpStatus.BAD_REQUEST);
                    return false;
                }
                String traceparent = headers.getFirst("traceparent");
                if (isW3cTraceparent(traceparent)) {
                    // Session metadata only: individual location messages remain untraced.
                    attributes.put("traceparent", traceparent);
                }
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler wsHandler, Exception exception) {
                // No-op.
            }
        };
    }

    private static boolean isW3cTraceparent(String value) {
        return value != null && value.matches("^[\u0030-\u0039a-f]{2}-[\u0030-\u0039a-f]{32}-[\u0030-\u0039a-f]{16}-[\u0030-\u0039a-f]{2}$");
    }
}
