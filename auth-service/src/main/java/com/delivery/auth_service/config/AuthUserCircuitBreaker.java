package com.delivery.auth_service.config;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;

/** Circuit boundary for HTTP calls from Auth to User only. */
@Component
public class AuthUserCircuitBreaker {
    private final CircuitBreaker circuitBreaker;

    public AuthUserCircuitBreaker(UserServiceConfig properties, MeterRegistry meterRegistry) {
        UserServiceConfig.Circuit config = properties.getCircuit();
        CircuitBreakerConfig circuitConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(config.getFailureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(config.getSlidingWindowSize())
                .minimumNumberOfCalls(config.getSlidingWindowSize())
                .waitDurationInOpenState(Duration.ofSeconds(config.getWaitOpenSeconds()))
                .permittedNumberOfCallsInHalfOpenState(config.getPermittedHalfOpenCalls())
                .recordException(error -> !(error instanceof IllegalArgumentException))
                .build();
        circuitBreaker = CircuitBreaker.of("authUserService", circuitConfig);
        circuitBreaker.getEventPublisher().onStateTransition(event -> meterRegistry.counter(
                "delivery.circuit.state_transition", "circuit", "authUserService",
                "transition", event.getStateTransition().name()).increment());
    }

    public <T> T execute(Supplier<T> supplier) {
        try {
            return circuitBreaker.executeSupplier(supplier);
        } catch (CallNotPermittedException exception) {
            throw new IllegalStateException("User service circuit is open", exception);
        }
    }

    CircuitBreaker circuitBreaker() { return circuitBreaker; }
}
