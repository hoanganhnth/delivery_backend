package com.delivery.observability;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxTraceContextTest {
    @Test
    void restoresPersistedW3cParentForKafkaInstrumentation() {
        String traceparent = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
        Map<String, String> headers = new HashMap<>();

        try (var ignored = OutboxTraceContext.open(traceparent)) {
            W3CTraceContextPropagator.getInstance().inject(
                    Context.current(), headers, Map::put);
        }

        assertThat(headers).containsEntry("traceparent", traceparent);
    }
}
