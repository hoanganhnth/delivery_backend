package com.delivery.notification_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConfigTest {

    @Test
    void usesConfiguredConsumerIdentityAndStartupPolicy() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "notification-test-group", false);

        assertThat(config.producerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092");
        assertThat(config.consumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "notification-test-group");
        assertThat(config.retryKafkaTemplate().getProducerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.StringSerializer.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void poisonNotificationEventIsPublishedUnchangedToSamePartitionDlt() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "notification-test-group", true);
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var recoverer = config.notificationDeadLetterRecoverer(template);
        ConsumerRecord<String, String> source = new ConsumerRecord<>(
                "delivery.status-updated", 4, 9L, "delivery-101", "bad-json");

        recoverer.accept(source, new IllegalArgumentException("poison"));

        ArgumentCaptor<ProducerRecord<String, String>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("delivery.status-updated.notification.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(4);
        assertThat(sent.getValue().key()).isEqualTo("delivery-101");
        assertThat(sent.getValue().value()).isEqualTo("bad-json");
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryTopicFallbackReturnsToTheCanonicalNotificationDlt() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "notification-test-group", true);
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var recoverer = config.notificationDeadLetterRecoverer(template);
        ConsumerRecord<String, String> retry = new ConsumerRecord<>(
                "delivery.status-updated-retry-notification-1000", 4, 9L,
                "delivery-101", "bad-json");

        recoverer.accept(retry, new IllegalArgumentException("poison"));

        ArgumentCaptor<ProducerRecord<String, String>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("delivery.status-updated.notification.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(4);
    }

    @Test
    void errorHandlerCommitsOnlyAfterFiniteRetryRecovery() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "notification-test-group", true);
        var recoverer = config.notificationDeadLetterRecoverer(mock(KafkaTemplate.class));
        var handler = config.notificationKafkaErrorHandler(recoverer, mock(MeterRegistry.class));

        assertThat(handler.isAckAfterHandle()).isTrue();
        assertThat(handler.seeksAfterHandling()).isTrue();
    }
}
