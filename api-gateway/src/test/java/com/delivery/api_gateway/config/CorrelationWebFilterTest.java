package com.delivery.api_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.observability.CorrelationId;
import com.delivery.observability.CorrelationWebFilter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class CorrelationWebFilterTest {
    private final CorrelationWebFilter filter = new CorrelationWebFilter();

    @Test
    void generatesAndReturnsCorrelationIdWhenClientDoesNotSendOne() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/health"));
        AtomicReference<String> reactiveContext = new AtomicReference<>();

        filter.filter(exchange, chained -> Mono.deferContextual(context -> {
            reactiveContext.set(context.get(CorrelationId.MDC_KEY));
            assertThat(chained.getRequest().getHeaders().getFirst(CorrelationId.HEADER))
                    .isEqualTo(reactiveContext.get());
            return Mono.empty();
        })).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER))
                .matches("[A-Za-z0-9._:-]{1,64}");
        assertThat(reactiveContext.get()).isEqualTo(exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER));
    }

    @Test
    void preservesValidClientCorrelationIdAndRejectsInvalidOne() {
        MockServerWebExchange valid = MockServerWebExchange.from(MockServerHttpRequest.get("/health")
                .header(CorrelationId.HEADER, "mobile-42:checkout"));
        filter.filter(valid, ignored -> Mono.empty()).block();
        assertThat(valid.getResponse().getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo("mobile-42:checkout");

        MockServerWebExchange invalid = MockServerWebExchange.from(MockServerHttpRequest.get("/health")
                .header(CorrelationId.HEADER, "bad value"));
        filter.filter(invalid, ignored -> Mono.empty()).block();
        assertThat(invalid.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
