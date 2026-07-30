package com.delivery.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationIdTest {
    @AfterEach void clearMdc() { MDC.clear(); }

    @Test
    void createsWhenMissingAndAcceptsSafeClientValue() {
        assertThat(CorrelationId.requireValidOrCreate(null)).matches("[A-Za-z0-9._:-]{1,64}");
        assertThat(CorrelationId.requireValidOrCreate("mobile-42:checkout")).isEqualTo("mobile-42:checkout");
    }

    @Test
    void rejectsHeaderInjectionAndOversizedValues() {
        assertThatThrownBy(() -> CorrelationId.requireValidOrCreate("bad\\r\\nX-Role: ADMIN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrelationId.requireValidOrCreate("a".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propagatesMdcToKafkaAndRestoresItForConsumer() {
        MDC.put(CorrelationId.MDC_KEY, "gateway-order-17");
        ProducerRecord<Object, Object> outbound = new CorrelationKafkaProducerInterceptor()
                .onSend(new ProducerRecord<>("order.created", "17", "{}"));
        assertThat(new String(outbound.headers().lastHeader(CorrelationId.HEADER).value(), StandardCharsets.UTF_8))
                .isEqualTo("gateway-order-17");
        ConsumerRecord<Object, Object> inbound = new ConsumerRecord<>("order.created", 0, 0, "17", "{}");
        inbound.headers().add(CorrelationId.HEADER, "gateway-order-17".getBytes(StandardCharsets.UTF_8));
        CorrelationKafkaRecordInterceptor interceptor = new CorrelationKafkaRecordInterceptor();
        interceptor.intercept(inbound, null);
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("gateway-order-17");
        interceptor.afterRecord(inbound, null);
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void redactsCredentialsFromExceptionText() {
        assertThat(SafeLog.exceptionMessage(new IllegalStateException("Authorization=Bearer-top-secret password=hunter2")))
                .doesNotContain("top-secret", "hunter2")
                .contains("[REDACTED]");
    }
}
