package com.delivery.observability;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.kafka.listener.RecordInterceptor;

/** Establishes and clears MDC around each listener invocation. */
public final class CorrelationKafkaRecordInterceptor implements RecordInterceptor<Object, Object> {
    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        var header = record.headers().lastHeader(CorrelationId.HEADER);
        String value = header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
        // Kafka is an internal boundary: old or malformed records get a fresh
        // ID rather than preventing recovery/DLT handling.
        org.slf4j.MDC.put(CorrelationId.MDC_KEY,
                CorrelationId.isValid(value) ? value : CorrelationId.create());
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, org.apache.kafka.clients.consumer.Consumer<Object, Object> consumer) {
        org.slf4j.MDC.remove(CorrelationId.MDC_KEY);
    }
}
