package com.delivery.api_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class LivestreamDisabledWebFilterTest {

    private final LivestreamDisabledWebFilter filter = new LivestreamDisabledWebFilter();

    @Test
    void disabledLivestreamSurfaceReturnsStableErrorWithoutCallingDownstream() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/livestreams/active").build());
        WebFilterChain downstream = ignored -> Mono.error(new AssertionError("must not call downstream"));

        filter.filter(exchange, downstream).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .isEqualTo("application/json");
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"status\":0")
                .contains("\"code\":\"LIVESTREAM_DISABLED\"");
    }

    @Test
    void unrelatedPathsContinueThroughTheGateway() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/42").build());
        boolean[] called = {false};
        WebFilterChain downstream = ignored -> {
            called[0] = true;
            return Mono.empty();
        };

        filter.filter(exchange, downstream).block();

        assertThat(called[0]).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
