package com.delivery.restaurant_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RestaurantOrderCircuitBreakerTest {
    @Test
    void opensAfterOrderFailuresAndClosesAfterRecovery() {
        OrderCallResilienceProperties properties = new OrderCallResilienceProperties();
        properties.setSlidingWindowSize(2);
        properties.setPermittedHalfOpenCalls(1);
        RestaurantOrderCircuitBreaker breaker = new RestaurantOrderCircuitBreaker(properties, new SimpleMeterRegistry());

        assertThatThrownBy(() -> breaker.execute(() -> { throw new IllegalStateException("order 5xx"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> breaker.execute(() -> { throw new IllegalStateException("order timeout"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(breaker.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> breaker.execute(() -> "not called")).hasMessageContaining("circuit is open");

        breaker.circuitBreaker().transitionToHalfOpenState();
        assertThat(breaker.execute(() -> "recovered")).isEqualTo("recovered");
        assertThat(breaker.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
