package com.delivery.api_gateway.ratelimit;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
 * Applies fixed-window rate limiting by peer IP (or the client IP supplied by a
 * configured trusted proxy) across all public and protected route categories.
 */
@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {
    private static final String RATE_LIMIT_PREFIX = "delivery:gateway:rate-limit:";

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

        String ip = peerIp(exchange);

        if ("/ws/shipper-locations".equals(path)) {
            return new Policy("websocket_connection", properties.getWebsocketConnection(), ignored -> ip);
        }
        if (isPublicAuth(path, method)) {
            return new Policy("public_auth", properties.getPublicAuth(), ignored -> ip);
        }
        if (isUserRegistration(path, method)) {
            return new Policy("user_registration", properties.getPublicAuth(), ignored -> ip);
        }
        if (isPublicCatalog(path, method)) {
            return new Policy("public_catalog", properties.getPublicCatalog(), ignored -> ip);
        }
        if (method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS) {
            return new Policy("authenticated_read", properties.getAuthenticatedRead(), ignored -> ip);
        }
        return new Policy("mutation", properties.getMutation(), ignored -> ip);
    }

    private boolean isPublicAuth(String path, HttpMethod method) {
        return (method == HttpMethod.GET && path.matches("/api/auth/registrations/[^/]+"))
                || (method == HttpMethod.POST && ("/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/social-login".equals(path)
                || "/api/auth/refresh-token".equals(path)
                || "/api/auth/logout".equals(path)
                || "/api/auth/forgot-password".equals(path)
                || "/api/auth/reset-password".equals(path)
                || "/api/auth/email-verification/request".equals(path)
                || "/api/auth/email-verification/confirm".equals(path)));
    }

    private boolean isUserRegistration(String path, HttpMethod method) {
        return method == HttpMethod.POST && "/api/users/registrations".equals(path);
    }

    private boolean isPublicCatalog(String path, HttpMethod method) {
        if (method != HttpMethod.GET) {
            return false;
        }
        return "/api/search/restaurants".equals(path)
                || "/api/search/dishes".equals(path)
                || "/api/restaurants".equals(path)
                || "/api/restaurants/page".equals(path)
                || "/api/restaurants/search".equals(path)
                || path.matches("/api/restaurants/[0-9]+(?:/ratings)?")
                || path.matches("/api/restaurants/[0-9]+/ratings/page")
                || path.matches("/api/menu-items/restaurant/[0-9]+(?:/(?:available/)?page|/available)?");
    }

    private String peerIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String directIp = remoteAddress == null || remoteAddress.getAddress() == null ? "unknown" : remoteAddress.getAddress().getHostAddress();

        if (properties.isTrustedProxy() && isTrustedProxyIp(directIp)) {
            String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
        }
        return directIp;
    }

    private boolean isTrustedProxyIp(String ip) {
        if ("unknown".equals(ip) || ip == null || properties.getTrustedProxyCidrs().isEmpty()) {
            return false;
        }
        return properties.getTrustedProxyCidrs().stream()
                .filter(cidr -> cidr != null && !cidr.isBlank())
                .anyMatch(cidr -> matchesCidr(ip, cidr.trim()));
    }

    private boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/", -1);
            if (parts.length != 2) {
                return false;
            }
            byte[] address = InetAddress.getByName(ip).getAddress();
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            int prefixLength = Integer.parseInt(parts[1]);
            if (address.length != network.length || prefixLength < 0 || prefixLength > address.length * 8) {
                return false;
            }
            int completeBytes = prefixLength / 8;
            for (int i = 0; i < completeBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (address[completeBytes] & mask) == (network[completeBytes] & mask);
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
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
