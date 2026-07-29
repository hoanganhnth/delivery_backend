package com.delivery.tracking_service.config;

import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebSocketConfigAuthenticationTest {

    @Test
    void handshakeCopiesTrustedGatewayIdentity() throws Exception {
        ShipperLocationWebSocketHandler handler = mock(ShipperLocationWebSocketHandler.class);
        WebSocketConfig config = new WebSocketConfig(handler, "http://localhost:5173");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "42");
        headers.add("X-Role", "SHIPPER");
        when(request.getHeaders()).thenReturn(headers);
        var attributes = new HashMap<String, Object>();

        boolean accepted = config.identityHeadersInterceptor().beforeHandshake(
                request, response, mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry("authenticatedUserId", 42L)
                .containsEntry("authenticatedRole", "SHIPPER");
    }

    @Test
    void handshakeRejectsMissingIdentity() throws Exception {
        WebSocketConfig config = new WebSocketConfig(
                mock(ShipperLocationWebSocketHandler.class), "http://localhost:5173");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        boolean accepted = config.identityHeadersInterceptor().beforeHandshake(
                request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
