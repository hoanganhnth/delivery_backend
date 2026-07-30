package com.delivery.api_gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;

class GatewayRateLimitFilterTest {
    @Test
    void rejectsRequestPastPublicAuthBoundaryWithStandardEnvelope() {
        StubStore store = new StubStore(Mono.just(new RateLimitStore.Decision(11, 42)));
        var exchange = exchange("POST", "/api/auth/login", null, null);
        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);

        filter(store).filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("42");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"status\":0", "\"data\":null", "Rate limit exceeded");
        assertThat(store.key.get()).contains("public_auth:127.0.0.1");
    }

    @Test
    void usesVerifiedSubjectForAuthenticatedRead() {
        StubStore store = new StubStore(Mono.just(new RateLimitStore.Decision(1, 60)));
        var exchange = exchange("GET", "/api/orders/7", "44", "USER");
        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);
        org.mockito.Mockito.when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter(store).filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertThat(store.key.get()).contains("authenticated_read:44");
    }

    @Test
    void catalogFailsOpenButAuthFailsClosedWhenRedisIsUnavailable() {
        StubStore failingStore = new StubStore(Mono.error(new IllegalStateException("Redis down")));
        var catalogExchange = exchange("GET", "/api/search/dishes", null, null);
        var catalogChain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);
        org.mockito.Mockito.when(catalogChain.filter(catalogExchange)).thenReturn(Mono.empty());

        filter(failingStore).filter(catalogExchange, catalogChain).block();

        verify(catalogChain).filter(catalogExchange);

        var authExchange = exchange("POST", "/api/auth/login", null, null);
        var authChain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);
        filter(failingStore).filter(authExchange, authChain).block();

        assertThat(authExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void catalogFailsOpenAndAuthFailsClosedWhenRedisTimesOut() {
        StubStore hangingStore = new StubStore(Mono.never());
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRedisTimeoutMillis(10);

        var catalogExchange = exchange("GET", "/api/search/dishes", null, null);
        var catalogChain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);
        org.mockito.Mockito.when(catalogChain.filter(catalogExchange)).thenReturn(Mono.empty());
        filter(hangingStore, properties).filter(catalogExchange, catalogChain)
                .block(Duration.ofSeconds(1));
        verify(catalogChain).filter(catalogExchange);

        var authExchange = exchange("POST", "/api/auth/login", null, null);
        filter(hangingStore, properties).filter(authExchange, mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class))
                .block(Duration.ofSeconds(1));
        assertThat(authExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void limitsOnlyWebsocketHandshakeBySubject() {
        StubStore store = new StubStore(Mono.just(new RateLimitStore.Decision(1, 60)));
        var exchange = exchange("GET", "/ws/shipper-locations", "shipper-9", "SHIPPER");
        var chain = mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);
        org.mockito.Mockito.when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter(store).filter(exchange, chain).block();

        assertThat(store.key.get()).contains("websocket_connection:shipper-9");
    }

    private GatewayRateLimitFilter filter(RateLimitStore store) {
        return filter(store, new RateLimitProperties());
    }

    private GatewayRateLimitFilter filter(RateLimitStore store, RateLimitProperties properties) {
        return new GatewayRateLimitFilter(store, properties, new ObjectMapper(), new SimpleMeterRegistry());
    }

    private MockServerWebExchange exchange(String method, String path, String userId, String role) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path)
                .remoteAddress(new InetSocketAddress("127.0.0.1", 50123));
        if (userId != null) builder.header("X-User-Id", userId);
        if (role != null) builder.header("X-Role", role);
        return MockServerWebExchange.from(builder);
    }

    private static final class StubStore implements RateLimitStore {
        private final Mono<Decision> result;
        private final AtomicReference<String> key = new AtomicReference<>();

        private StubStore(Mono<Decision> result) { this.result = result; }

        @Override
        public Mono<Decision> increment(String key, int limit, long windowSeconds) {
            this.key.set(key);
            return result;
        }
    }
}
