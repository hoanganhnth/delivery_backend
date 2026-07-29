package com.delivery.api_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
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
}
