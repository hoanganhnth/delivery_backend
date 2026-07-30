package com.delivery.observability;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public final class CorrelationWebFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final String correlationId;
        try {
            correlationId = CorrelationId.requireValidOrCreate(exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));
        } catch (IllegalArgumentException ex) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }
        ServerHttpRequest propagatedRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CorrelationId.HEADER, correlationId))
                .build();
        ServerWebExchange propagatedExchange = exchange.mutate().request(propagatedRequest).build();
        propagatedExchange.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);
        return chain.filter(propagatedExchange)
                .contextWrite(Context.of(CorrelationId.MDC_KEY, correlationId))
                .doOnEach(signal -> signal.getContextView().getOrEmpty(CorrelationId.MDC_KEY)
                        .ifPresent(value -> org.slf4j.MDC.put(CorrelationId.MDC_KEY, String.valueOf(value))))
                .doFinally(signal -> org.slf4j.MDC.remove(CorrelationId.MDC_KEY));
    }
}
