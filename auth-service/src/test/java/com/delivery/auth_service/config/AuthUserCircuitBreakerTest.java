package com.delivery.auth_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AuthUserCircuitBreakerTest {
    @Test
    void transitionsClosedOpenAndHalfOpenWithoutReturningFallbackSuccess() {
        UserServiceConfig properties = new UserServiceConfig();
        properties.getCircuit().setSlidingWindowSize(2);
        properties.getCircuit().setFailureRateThreshold(50);
        properties.getCircuit().setPermittedHalfOpenCalls(1);
        AuthUserCircuitBreaker breaker = new AuthUserCircuitBreaker(properties, new SimpleMeterRegistry());

        assertThatThrownBy(() -> breaker.execute(() -> { throw new IllegalStateException("user 5xx"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> breaker.execute(() -> { throw new IllegalStateException("user timeout"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(breaker.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> breaker.execute(() -> "must not execute"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User service circuit is open");

        breaker.circuitBreaker().transitionToHalfOpenState();
        assertThat(breaker.execute(() -> "recovered")).isEqualTo("recovered");
        assertThat(breaker.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
