package com.delivery.api_gateway.config;

import java.util.Arrays;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Removes obsolete client-supplied identity headers before requests are routed.
 * Resource services derive the actor from the bearer token they validate via JWKS.
 */
@Component
public class TrustedIdentityHeaderFilter implements GlobalFilter, Ordered {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String ROLE_HEADER = "X-Role";
    private static final String WEBSOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";

    @Override
    public Mono filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isTrackingWebSocket(exchange.getRequest())
                && !hasTrackingWebSocketCredential(exchange.getRequest().getHeaders())) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            return exchange.getResponse().setComplete();
        }

        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(ROLE_HEADER);
                })
                .build();
        ServerWebExchange sanitized = exchange.mutate().request(sanitizedRequest).build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isTrackingWebSocket(ServerHttpRequest request) {
        return "/ws/shipper-locations".equals(request.getPath().value());
    }

    private boolean hasTrackingWebSocketCredential(HttpHeaders headers) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")
                && !authorization.substring("Bearer ".length()).isBlank()) {
            return true;
        }

        return headers.getOrEmpty(WEBSOCKET_PROTOCOL_HEADER).stream()
                .flatMap(protocols -> Arrays.stream(protocols.split(",")))
                .map(String::trim)
                .anyMatch(protocol -> protocol.startsWith("bearer.")
                        && protocol.length() > "bearer.".length());
    }
}
