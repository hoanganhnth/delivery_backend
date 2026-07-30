package com.delivery.observability;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.List;

/** Restores only W3C trace context while a durable outbox record is relayed. */
public final class OutboxTraceContext {
    private OutboxTraceContext() { }

    public static Scope open(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return () -> { };
        }
        Context parent = W3CTraceContextPropagator.getInstance().extract(
                Context.current(), traceparent, new TextMapGetter<>() {
                    @Override public Iterable<String> keys(String carrier) { return List.of("traceparent"); }
                    @Override public String get(String carrier, String key) {
                        return "traceparent".equals(key) ? carrier : null;
                    }
                });
        return parent.makeCurrent();
    }
}
