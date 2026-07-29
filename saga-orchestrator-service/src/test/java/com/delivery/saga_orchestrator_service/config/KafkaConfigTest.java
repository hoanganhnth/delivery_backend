package com.delivery.saga_orchestrator_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConfigTest {

    @Test
    void usesConfiguredBootstrapServersForProducerAndConsumer() {
        KafkaConfig config = new KafkaConfig("kafka:19092", false);

        assertThat(config.producerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092");
        assertThat(config.consumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092");
    }

    @Test
    void usesConfiguredGroupAndListenerStartupPolicy() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "saga-b8-status", false);
        var factory = config.kafkaListenerContainerFactory(
                config.sagaKafkaErrorHandler(config.sagaDeadLetterRecoverer(mock(KafkaTemplate.class))));

        assertThat(config.consumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "saga-b8-status");
        assertThat(ReflectionTestUtils.getField(factory, "autoStartup")).isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void poisonSagaEventIsPublishedUnchangedToSamePartitionDlt() {
        KafkaConfig config = new KafkaConfig("kafka:19092", true);
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var recoverer = config.sagaDeadLetterRecoverer(template);
        ConsumerRecord<String, Object> source = new ConsumerRecord<>(
                "delivery.created.result", 2, 9L, "order-101", "bad-json");

        recoverer.accept(source, new IllegalArgumentException("poison"));

        ArgumentCaptor<ProducerRecord<String, Object>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("delivery.created.result.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(2);
        assertThat(sent.getValue().key()).isEqualTo("order-101");
        assertThat(sent.getValue().value()).isEqualTo("bad-json");
    }

    @Test
    void errorHandlerCommitsOnlyAfterFiniteRetryRecovery() {
        KafkaConfig config = new KafkaConfig("kafka:19092", true);
        var recoverer = config.sagaDeadLetterRecoverer(mock(KafkaTemplate.class));
        var handler = config.sagaKafkaErrorHandler(recoverer);

        assertThat(handler.isAckAfterHandle()).isTrue();
        assertThat(handler.seeksAfterHandling()).isTrue();
    }
}
