package com.delivery.api_gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Removes client-supplied identity headers before route filters run.
 * Authenticated routes add trusted values back from the verified JWT.
 */
@Component
public class TrustedIdentityHeaderFilter implements GlobalFilter, Ordered {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String ROLE_HEADER = "X-Role";

    @Override
    public Mono filter(ServerWebExchange exchange, GatewayFilterChain chain) {
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
}
