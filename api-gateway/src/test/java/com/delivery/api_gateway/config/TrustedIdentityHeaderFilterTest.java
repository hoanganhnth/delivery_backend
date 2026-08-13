package com.delivery.api_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

class TrustedIdentityHeaderFilterTest {

    @Test
    void stripsSpoofedIdentityHeadersEvenOnPublicRoutes() {
        TrustedIdentityHeaderFilter filter = new TrustedIdentityHeaderFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/restaurants")
                        .header("X-User-Id", "999")
                        .header("X-Role", "ADMIN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, sanitized -> {
            chainCalled.set(true);
            assertThat(sanitized.getRequest().getHeaders()).doesNotContainKeys(
                    "X-User-Id", "X-Role");
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void rejectsUnauthenticatedTrackingWebSocketBeforeUpgrade() {
        TrustedIdentityHeaderFilter filter = new TrustedIdentityHeaderFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/shipper-locations").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, ignored -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("WWW-Authenticate"))
                .isEqualTo("Bearer");
    }

    @Test
    void acceptsBrowserWebSocketBearerProtocolForDownstreamJwtValidation() {
        TrustedIdentityHeaderFilter filter = new TrustedIdentityHeaderFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/shipper-locations")
                        .header("Sec-WebSocket-Protocol", "chat, bearer.opaque-token")
                        .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, sanitized -> {
            chainCalled.set(true);
            assertThat(sanitized.getRequest().getHeaders().getFirst("Sec-WebSocket-Protocol"))
                    .isEqualTo("chat, bearer.opaque-token");
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
    }
}
