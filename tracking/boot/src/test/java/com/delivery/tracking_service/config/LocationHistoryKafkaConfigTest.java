package com.delivery.tracking_service.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationHistoryKafkaConfigTest {

    @Test
    void consumerGroupAndRawRetryTemplateFollowConfiguredTrackingBoundary() {
        LocationHistoryKafkaConfig config = new LocationHistoryKafkaConfig(
                "kafka:19092", "tracking-history-test", false);

        assertThat(config.locationHistoryConsumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "tracking-history-test");
        assertThat(config.trackingRetryKafkaTemplate().getProducerFactory().getConfigurationProperties())
                .containsEntry(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.StringSerializer.class);
        assertThat(config.trackingKafkaTemplate().getProducerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        org.springframework.kafka.support.serializer.JsonSerializer.class)
                .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void poisonRecordReachesSamePartitionTrackingDltUnchanged() {
        LocationHistoryKafkaConfig config = new LocationHistoryKafkaConfig(
                "kafka:19092", "tracking-history-test", true);
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        var recoverer = config.locationHistoryDeadLetterRecoverer(template);
        ConsumerRecord<String, String> source = new ConsumerRecord<>(
                "shipper.location-updated", 3, 9L, "42", "bad-json");
        recoverer.accept(source, new IllegalArgumentException("poison"));

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("shipper.location-updated.tracking.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(3);
        assertThat(sent.getValue().key()).isEqualTo("42");
        assertThat(sent.getValue().value()).isEqualTo("bad-json");
    }

    @Test
    void retryTopicFallbackUsesCanonicalTrackingDltAndRecoversAfterFiniteRetry() {
        assertThat(LocationHistoryKafkaConfig.ownerDltTopic(
                "shipper.location-updated-retry-tracking-1000"))
                .isEqualTo("shipper.location-updated.tracking.DLT");

        LocationHistoryKafkaConfig config = new LocationHistoryKafkaConfig(
                "kafka:19092", "tracking-history-test", true);
        var handler = config.locationHistoryKafkaErrorHandler(
                config.locationHistoryDeadLetterRecoverer(mock(KafkaTemplate.class)),
                new SimpleMeterRegistry());
        assertThat(handler.isAckAfterHandle()).isTrue();
        assertThat(handler.seeksAfterHandling()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void routingPoisonReachesSamePartitionTrackingDltUnchanged() {
        LocationHistoryKafkaConfig config = new LocationHistoryKafkaConfig(
                "kafka:19092", "tracking-history-test", true);
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        var recoverer = config.deliveryRoomsDeadLetterRecoverer(template);
        ConsumerRecord<String, String> source = new ConsumerRecord<>(
                "shipper.status-change", 2, 9L, "42", "bad-json");
        recoverer.accept(source, new IllegalArgumentException("poison"));

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("shipper.status-change.tracking.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(2);
        assertThat(sent.getValue().value()).isEqualTo("bad-json");
    }
}
