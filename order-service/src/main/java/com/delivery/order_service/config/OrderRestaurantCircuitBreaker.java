package com.delivery.order_service.config;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;

/** Isolates only outbound Order -> Restaurant HTTP calls. */
@Component
public class OrderRestaurantCircuitBreaker {
    private final CircuitBreaker circuitBreaker;
    private final Duration timeout;

    public OrderRestaurantCircuitBreaker(RestaurantCallResilienceProperties properties, MeterRegistry meterRegistry) {
        timeout = Duration.ofMillis(properties.getTimeoutMs());
        circuitBreaker = CircuitBreaker.of("orderRestaurantService", CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getFailureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getSlidingWindowSize())
                .waitDurationInOpenState(Duration.ofSeconds(properties.getWaitOpenSeconds()))
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedHalfOpenCalls())
                .build());
        circuitBreaker.getEventPublisher().onStateTransition(event -> meterRegistry.counter(
                "delivery.circuit.state_transition", "circuit", "orderRestaurantService",
                "transition", event.getStateTransition().name()).increment());
    }

    public <T> T execute(Supplier<T> supplier) {
        try {
            return circuitBreaker.executeSupplier(supplier);
        } catch (CallNotPermittedException exception) {
            throw new IllegalStateException("Restaurant service circuit is open", exception);
        }
    }

    CircuitBreaker circuitBreaker() { return circuitBreaker; }
    public Duration timeout() { return timeout; }
}
