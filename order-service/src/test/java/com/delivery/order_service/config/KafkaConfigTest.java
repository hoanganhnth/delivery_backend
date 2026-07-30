package com.delivery.order_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class KafkaConfigTest {

    @Test
    void usesConfiguredBootstrapServersForProducerAndConsumer() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "order-recovery", false);

        assertThat(config.producerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092");
        assertThat(config.consumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "order-recovery");

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.kafkaListenerContainerFactory(new com.fasterxml.jackson.databind.ObjectMapper(),
                        config.orderKafkaErrorHandler(
                                config.orderDeadLetterRecoverer(mock(KafkaTemplate.class)),
                                mock(com.delivery.order_service.metrics.BusinessMetrics.class)));
        assertThat(ReflectionTestUtils.getField(factory, "autoStartup")).isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void poisonRecordIsPublishedToSamePartitionDeadLetterTopic() {
        KafkaConfig config = new KafkaConfig("kafka:19092");
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var recoverer = config.orderDeadLetterRecoverer(template);
        ConsumerRecord<String, Object> source = new ConsumerRecord<>(
                "restaurant.order-confirmed", 3, 7L, "order-9", "bad-json");
        source.headers().add("eventId", "11111111-1111-1111-1111-111111111111"
                .getBytes(StandardCharsets.UTF_8));
        source.headers().add("correlationId", "corr-restaurant-9".getBytes(StandardCharsets.UTF_8));

        recoverer.accept(source, new IllegalArgumentException("poison"));

        ArgumentCaptor<ProducerRecord<String, Object>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("restaurant.order-confirmed.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(3);
        assertThat(sent.getValue().key()).isEqualTo("order-9");
        assertThat(sent.getValue().value()).isEqualTo("bad-json");
        assertThat(sent.getValue().headers().lastHeader(org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_TOPIC))
                .isNotNull();
        assertThat(sent.getValue().headers().lastHeader(org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_PARTITION))
                .isNotNull();
        assertThat(sent.getValue().headers().lastHeader(org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_OFFSET))
                .isNotNull();
        assertThat(sent.getValue().headers().lastHeader(org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_FQCN))
                .isNotNull();
        assertThat(new String(sent.getValue().headers().lastHeader("eventId").value(), StandardCharsets.UTF_8))
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(new String(sent.getValue().headers().lastHeader("correlationId").value(), StandardCharsets.UTF_8))
                .isEqualTo("corr-restaurant-9");
    }

    @Test
    void errorHandlerCommitsOnlyAfterFiniteRetryRecovery() {
        KafkaConfig config = new KafkaConfig("kafka:19092");
        var recoverer = config.orderDeadLetterRecoverer(mock(KafkaTemplate.class));
        var handler = config.orderKafkaErrorHandler(recoverer,
                mock(com.delivery.order_service.metrics.BusinessMetrics.class));

        assertThat(handler.isAckAfterHandle()).isTrue();
        assertThat(handler.seeksAfterHandling()).isTrue();
    }
}
