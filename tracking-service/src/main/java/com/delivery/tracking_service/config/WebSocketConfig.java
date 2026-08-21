package com.delivery.tracking_service.config;

import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import com.delivery.observability.CorrelationId;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;
import java.util.Locale;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final List<String> TRACKING_ROLE_PRECEDENCE = List.of("ADMIN", "SHIPPER", "USER");

    private final ShipperLocationWebSocketHandler shipperLocationHandler;
    private final JwtDecoder jwtDecoder;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            ShipperLocationWebSocketHandler shipperLocationHandler,
            JwtDecoder jwtDecoder,
            @Value("${app.websocket.allowed-origins:http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173,http://127.0.0.1:3000}")
            String allowedOrigins) {
        this.shipperLocationHandler = shipperLocationHandler;
        this.jwtDecoder = jwtDecoder;
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
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
                String authHeader = headers.getFirst("Authorization");
                String token = null;

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                } else {
                    List<String> protocols = headers.get("Sec-WebSocket-Protocol");
                    if (protocols != null && !protocols.isEmpty()) {
                        for (String protocol : protocols) {
                            for (String part : protocol.split(",")) {
                                String trimmed = part.trim();
                                if (trimmed.startsWith("bearer.")) {
                                    token = trimmed.substring(7);
                                    break;
                                }
                            }
                        }
                    }
                }

                Long legacyUserId = null;
                Long principalId = null;
                Long identityClaimsVersion = null;
                String role = null;

                if (token != null && !token.isBlank()) {
                    try {
                        Jwt jwt = jwtDecoder.decode(token);
                        // WebSocket must obey the same dual-claim rule as
                        // HTTP resource endpoints. `sub` is deliberately not
                        // read: R5 changes it from legacy profile ID to Auth
                        // principal ID and treating it as a profile would map
                        // a shipper request to the wrong aggregate.
                        legacyUserId = positiveLong(jwt.getClaim("legacy_user_id"));
                        principalId = positiveLong(jwt.getClaim("principal_id"));
                        identityClaimsVersion = positiveLong(jwt.getClaim("identity_claims_version"));
                        role = selectTrackingRole(jwt);
                    } catch (JwtException e) {
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return false;
                    }
                }

                if (legacyUserId == null || principalId == null || identityClaimsVersion == null
                        || identityClaimsVersion != 1L || role == null || role.isBlank()) {
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                }

                attributes.put("authenticatedUserId", legacyUserId);
                attributes.put("authenticatedPrincipalId", principalId);
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
                    attributes.put("traceparent", traceparent);
                }
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler wsHandler, Exception exception) {
            }
        };
    }

    private static boolean isW3cTraceparent(String value) {
        return value != null && value.matches("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");
    }

    private static Long positiveLong(Object value) {
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed > 0 ? parsed : null;
        }
        if (!(value instanceof String raw) || !raw.matches("\\d+")) {
            return null;
        }
        try {
            long parsed = Long.parseLong(raw);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String selectTrackingRole(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            for (String preferredRole : TRACKING_ROLE_PRECEDENCE) {
                boolean present = roles.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(String::trim)
                        .map(value -> value.toUpperCase(Locale.ROOT))
                        .anyMatch(preferredRole::equals);
                if (present) {
                    return preferredRole;
                }
            }
        }

        String legacyRole = jwt.getClaimAsString("role");
        if (legacyRole == null) {
            return null;
        }
        String normalizedRole = legacyRole.trim().toUpperCase(Locale.ROOT);
        return TRACKING_ROLE_PRECEDENCE.contains(normalizedRole) ? normalizedRole : null;
    }
}
