package com.delivery.tracking_service.config;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;

/** Isolates the low-volume Tracking -> Delivery access check. */
@Component
public class TrackingDeliveryCircuitBreaker {
    private final CircuitBreaker circuitBreaker;

    public TrackingDeliveryCircuitBreaker(DeliveryCallResilienceProperties properties, MeterRegistry meterRegistry) {
        circuitBreaker = CircuitBreaker.of("trackingDeliveryService", CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getFailureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getSlidingWindowSize())
                .waitDurationInOpenState(Duration.ofSeconds(properties.getWaitOpenSeconds()))
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedHalfOpenCalls())
                .build());
        circuitBreaker.getEventPublisher().onStateTransition(event -> meterRegistry.counter(
                "delivery.circuit.state_transition", "circuit", "trackingDeliveryService",
                "transition", event.getStateTransition().name()).increment());
    }

    public <T> T execute(Supplier<T> supplier) {
        try { return circuitBreaker.executeSupplier(supplier); }
        catch (CallNotPermittedException exception) { throw new IllegalStateException("Delivery service circuit is open", exception); }
    }
    CircuitBreaker circuitBreaker() { return circuitBreaker; }
}
