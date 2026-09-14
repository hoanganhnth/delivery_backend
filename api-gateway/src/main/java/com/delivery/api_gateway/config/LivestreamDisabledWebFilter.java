package com.delivery.api_gateway.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(
        name = "app.livestream.client-api-enabled",
        havingValue = "false",
        matchIfMissing = true)
public class LivestreamDisabledWebFilter implements WebFilter {

    private static final byte[] DISABLED_RESPONSE = ("{\"status\":0,"
            + "\"message\":\"Tính năng livestream chưa được bật\","
            + "\"data\":null,"
            + "\"error\":{\"code\":\"LIVESTREAM_DISABLED\"}}")
            .getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.equals("/api/livestreams") && !path.startsWith("/api/livestreams/")) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setContentLength(DISABLED_RESPONSE.length);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(DISABLED_RESPONSE)));
    }
}
