package com.delivery.observability;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/** Copies the current context to records produced inside request or listener work. */
public final class CorrelationKafkaProducerInterceptor implements ProducerInterceptor<Object, Object> {
    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        if (record.headers().lastHeader(CorrelationId.HEADER) == null) {
            record.headers().add(CorrelationId.HEADER,
                    CorrelationContext.currentOrCreate().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
    @Override public void onAcknowledgement(RecordMetadata metadata, Exception exception) { }
    @Override public void close() { }
    @Override public void configure(Map<String, ?> configs) { }
}
