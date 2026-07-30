package com.delivery.tracking_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class TrackingDeliveryCircuitBreakerTest {
    @Test
    void failsClosedWhenDeliveryCircuitIsOpenAndRecoversThroughHalfOpen() {
        DeliveryCallResilienceProperties properties = new DeliveryCallResilienceProperties();
        properties.setSlidingWindowSize(2);
        properties.setPermittedHalfOpenCalls(1);
        TrackingDeliveryCircuitBreaker breaker = new TrackingDeliveryCircuitBreaker(properties, new SimpleMeterRegistry());

        assertThatThrownBy(() -> breaker.execute(() -> { throw new IllegalStateException("delivery 5xx"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> breaker.execute(() -> { throw new IllegalStateException("delivery timeout"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(breaker.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> breaker.execute(() -> true)).hasMessageContaining("circuit is open");

        breaker.circuitBreaker().transitionToHalfOpenState();
        assertThat(breaker.execute(() -> true)).isTrue();
        assertThat(breaker.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
