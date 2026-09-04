package com.delivery.tracking_service.config;

import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebSocketConfigAuthenticationTest {

    @Test
    void registerUsesConfiguredExactOriginsInsteadOfWildcardPatterns() {
        ShipperLocationWebSocketHandler handler = mock(ShipperLocationWebSocketHandler.class);
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/ws/shipper-locations")).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        when(registration.setAllowedOrigins(any(String[].class))).thenReturn(registration);

        WebSocketConfig config = new WebSocketConfig(
                handler,
                jwtDecoder,
                "http://localhost:5173, https://delivery.example");

        config.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOrigins("http://localhost:5173", "https://delivery.example");
        verify(registration, never()).setAllowedOriginPatterns(any(String[].class));
    }

    @Test
    void handshakeUsesJwtIdentity() throws Exception {
        ShipperLocationWebSocketHandler handler = mock(ShipperLocationWebSocketHandler.class);
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .claim("sub", "42")
                .claim("principal_id", 84)
                .claim("legacy_user_id", 42)
                .claim("identity_claims_version", 1)
                .claim("roles", List.of("SHIPPER"))
                .build();
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        WebSocketConfig config = new WebSocketConfig(handler, jwtDecoder, "http://localhost:5173");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer valid-token");
        when(request.getHeaders()).thenReturn(headers);
        var attributes = new HashMap<String, Object>();

        boolean accepted = config.identityHeadersInterceptor().beforeHandshake(
                request, response, mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry("authenticatedUserId", 42L)
                .containsEntry("authenticatedPrincipalId", 84L)
                .containsEntry("authenticatedRole", "SHIPPER");
    }

    @Test
    void handshakeSelectsShipperWhenTokenHasUserAndShipperRoles() throws Exception {
        ShipperLocationWebSocketHandler handler = mock(ShipperLocationWebSocketHandler.class);
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .claim("sub", "42")
                .claim("principal_id", 84)
                .claim("legacy_user_id", 42)
                .claim("identity_claims_version", 1)
                .claim("roles", List.of("USER", "SHIPPER"))
                .build();
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        WebSocketConfig config = new WebSocketConfig(handler, jwtDecoder, "http://localhost:5173");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer valid-token");
        when(request.getHeaders()).thenReturn(headers);
        var attributes = new HashMap<String, Object>();

        assertThat(config.identityHeadersInterceptor().beforeHandshake(
                request, response, mock(WebSocketHandler.class), attributes)).isTrue();

        assertThat(attributes).containsEntry("authenticatedRole", "SHIPPER");
    }

    @Test
    void handshakeRetainsValidTraceContextWithoutTracingLocationMessages() throws Exception {
        ShipperLocationWebSocketHandler handler = mock(ShipperLocationWebSocketHandler.class);
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .claim("sub", "42")
                .claim("principal_id", 84)
                .claim("legacy_user_id", 42)
                .claim("identity_claims_version", 1)
                .claim("roles", List.of("SHIPPER"))
                .build();
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        WebSocketConfig config = new WebSocketConfig(handler, jwtDecoder, "http://localhost:5173");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer valid-token");
        headers.add("X-Correlation-Id", "order-42");
        headers.add("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        when(request.getHeaders()).thenReturn(headers);
        var attributes = new HashMap<String, Object>();

        assertThat(config.identityHeadersInterceptor().beforeHandshake(
                request, response, mock(WebSocketHandler.class), attributes)).isTrue();

        assertThat(attributes).containsEntry("correlationId", "order-42")
                .containsEntry("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    }

    @Test
    void handshakeRejectsMissingIdentity() throws Exception {
        ShipperLocationWebSocketHandler handler = mock(ShipperLocationWebSocketHandler.class);
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        WebSocketConfig config = new WebSocketConfig(handler, jwtDecoder, "http://localhost:5173");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        boolean accepted = config.identityHeadersInterceptor().beforeHandshake(
                request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handshakeRejectsTokenThatOnlyCarriesLegacySubject() throws Exception {
        ShipperLocationWebSocketHandler handler = mock(ShipperLocationWebSocketHandler.class);
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        Jwt jwt = Jwt.withTokenValue("legacy-sub-only")
                .header("alg", "RS256")
                .claim("sub", "42")
                .claim("roles", List.of("SHIPPER"))
                .build();
        when(jwtDecoder.decode("legacy-sub-only")).thenReturn(jwt);
        WebSocketConfig config = new WebSocketConfig(handler, jwtDecoder, "http://localhost:5173");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer legacy-sub-only");
        when(request.getHeaders()).thenReturn(headers);

        assertThat(config.identityHeadersInterceptor().beforeHandshake(
                request, response, mock(WebSocketHandler.class), new HashMap<>())).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
