package com.delivery.match_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {
    private final MeterRegistry registry;
    public BusinessMetrics(MeterRegistry registry) { this.registry = registry; }
    public void record(String event) {
        Counter.builder("delivery.business.events").tag("event", event).tag("outcome", "success")
                .register(registry).increment();
    }
    public void kafka(String event) {
        Counter.builder("delivery.kafka.events").tag("event", event).register(registry).increment();
    }
}
