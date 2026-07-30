package com.delivery.api_gateway.ratelimit;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

/**
 * Applies the approved fixed-window policy after route JWT filters have added
 * trusted X-User-Id/X-Role headers. Public keys deliberately use the direct
 * peer IP; accepting client supplied forwarding headers would allow spoofing.
 */
@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {
    private static final String RATE_LIMIT_PREFIX = "delivery:gateway:rate-limit:";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-Role";

    private final RateLimitStore store;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public GatewayRateLimitFilter(RateLimitStore store, RateLimitProperties properties,
            ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.store = store;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public int getOrder() {
        // Route-specific JWT filters use the default order (0); this must run
        // afterwards so protected routes use the verified subject, not a header
        // supplied by a caller.
        return 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        Policy policy = policyFor(exchange);
        if (policy == null) {
            return chain.filter(exchange);
        }

        String key = RATE_LIMIT_PREFIX + policy.name().toLowerCase() + ':' + policy.key(exchange);
        return store.increment(key, policy.group().getLimit(), properties.getWindowSeconds())
                .timeout(Duration.ofMillis(properties.getRedisTimeoutMillis()))
                .onErrorResume(error -> {
                    increment("gateway.rate_limit.redis_failure", policy.name());
                    return policy.group().isFailOpen()
                            ? Mono.just(new RateLimitStore.Decision(0, 0))
                            : Mono.error(new RateLimitStoreUnavailableException());
                })
                .flatMap(decision -> decision.allowed(policy.group().getLimit())
                        ? chain.filter(exchange)
                        : reject(exchange, policy, decision.retryAfterSeconds()))
                .onErrorResume(RateLimitStoreUnavailableException.class,
                        error -> unavailable(exchange, policy));
    }

    private Policy policyFor(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        String subject = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
        String role = exchange.getRequest().getHeaders().getFirst(ROLE_HEADER);

        if ("/ws/shipper-locations".equals(path)) {
            return subject == null || subject.isBlank() ? null
                    : new Policy("websocket_connection", properties.getWebsocketConnection(), ignored -> subject);
        }
        if (isPublicAuth(path, method)) {
            return new Policy("public_auth", properties.getPublicAuth(), ignored -> peerIp(exchange));
        }
        if (isPublicCatalog(path, method)) {
            return new Policy("public_catalog", properties.getPublicCatalog(), ignored -> peerIp(exchange));
        }
        if (subject == null || subject.isBlank()) {
            return null;
        }
        if ("ADMIN".equals(role)) {
            return new Policy("admin", properties.getAdmin(), ignored -> subject);
        }
        if (method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS) {
            return new Policy("authenticated_read", properties.getAuthenticatedRead(), ignored -> subject);
        }
        return new Policy("mutation", properties.getMutation(), ignored -> subject);
    }

    private boolean isPublicAuth(String path, HttpMethod method) {
        return method == HttpMethod.POST && ("/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/social-login".equals(path)
                || "/api/auth/refresh-token".equals(path)
                || "/api/auth/logout".equals(path));
    }

    private boolean isPublicCatalog(String path, HttpMethod method) {
        if (method != HttpMethod.GET) {
            return false;
        }
        return "/api/search/restaurants".equals(path)
                || "/api/search/dishes".equals(path)
                || "/api/restaurants".equals(path)
                || "/api/restaurants/search".equals(path)
                || path.matches("/api/restaurants/[0-9]+(?:/ratings)?")
                || path.matches("/api/menu-items/restaurant/[0-9]+(?:/available)?");
    }

    private String peerIp(ServerWebExchange exchange) {
        InetSocketAddress address = exchange.getRequest().getRemoteAddress();
        return address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
    }

    private Mono<Void> reject(ServerWebExchange exchange, Policy policy, long retryAfterSeconds) {
        increment("gateway.rate_limit.rejected", policy.name());
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", Long.toString(retryAfterSeconds));
        return writeEnvelope(exchange, "Rate limit exceeded");
    }

    private Mono<Void> unavailable(ServerWebExchange exchange, Policy policy) {
        increment("gateway.rate_limit.redis_fail_closed", policy.name());
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return writeEnvelope(exchange, "Rate limiting is temporarily unavailable");
    }

    private Mono<Void> writeEnvelope(ServerWebExchange exchange, String message) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("status", 0);
            envelope.put("data", null);
            envelope.put("message", message);
            byte[] body = objectMapper.writeValueAsBytes(envelope);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return exchange.getResponse().setComplete();
        }
    }

    private void increment(String metric, String group) {
        Counter.builder(metric).tag("group", group).register(meterRegistry).increment();
    }

    private record Policy(String name, RateLimitProperties.Group group,
            java.util.function.Function<ServerWebExchange, String> keyResolver) {
        String key(ServerWebExchange exchange) { return keyResolver.apply(exchange); }
    }

    private static final class RateLimitStoreUnavailableException extends RuntimeException { }
}
