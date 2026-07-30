package com.delivery.restaurant_service.config;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;

/** Isolates rating and decision validation calls to Order. */
@Component
public class RestaurantOrderCircuitBreaker {
    private final CircuitBreaker circuitBreaker;

    public RestaurantOrderCircuitBreaker(OrderCallResilienceProperties properties, MeterRegistry meterRegistry) {
        circuitBreaker = CircuitBreaker.of("restaurantOrderService", CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getFailureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getSlidingWindowSize())
                .waitDurationInOpenState(Duration.ofSeconds(properties.getWaitOpenSeconds()))
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedHalfOpenCalls())
                .build());
        circuitBreaker.getEventPublisher().onStateTransition(event -> meterRegistry.counter(
                "delivery.circuit.state_transition", "circuit", "restaurantOrderService",
                "transition", event.getStateTransition().name()).increment());
    }

    public <T> T execute(Supplier<T> supplier) {
        try { return circuitBreaker.executeSupplier(supplier); }
        catch (CallNotPermittedException exception) { throw new IllegalStateException("Order service circuit is open", exception); }
    }
    CircuitBreaker circuitBreaker() { return circuitBreaker; }
}
