package com.delivery.observability;

import org.slf4j.MDC;

/** Small scoped MDC bridge; never stores user identity or request payloads. */
public final class CorrelationContext implements AutoCloseable {
    private final String previous;

    private CorrelationContext(String correlationId) {
        previous = MDC.get(CorrelationId.MDC_KEY);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
    }

    public static CorrelationContext with(String correlationId) {
        return new CorrelationContext(CorrelationId.requireValidOrCreate(correlationId));
    }

    public static String currentOrCreate() {
        String value = MDC.get(CorrelationId.MDC_KEY);
        return CorrelationId.isValid(value) ? value : CorrelationId.create();
    }

    @Override
    public void close() {
        if (previous == null) {
            MDC.remove(CorrelationId.MDC_KEY);
        } else {
            MDC.put(CorrelationId.MDC_KEY, previous);
        }
    }
}
