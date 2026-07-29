package com.delivery.notification_service.config;

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

class KafkaConfigTest {

    @Test
    void usesConfiguredConsumerIdentityAndStartupPolicy() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "notification-test-group", false);

        assertThat(config.producerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092");
        assertThat(config.consumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "notification-test-group");
    }

    @Test
    @SuppressWarnings("unchecked")
    void poisonNotificationEventIsPublishedUnchangedToSamePartitionDlt() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "notification-test-group", true);
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var recoverer = config.notificationDeadLetterRecoverer(template);
        ConsumerRecord<String, Object> source = new ConsumerRecord<>(
                "delivery.status-updated", 4, 9L, "delivery-101", "bad-json");

        recoverer.accept(source, new IllegalArgumentException("poison"));

        ArgumentCaptor<ProducerRecord<String, Object>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("delivery.status-updated.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(4);
        assertThat(sent.getValue().key()).isEqualTo("delivery-101");
        assertThat(sent.getValue().value()).isEqualTo("bad-json");
    }

    @Test
    void errorHandlerCommitsOnlyAfterFiniteRetryRecovery() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "notification-test-group", true);
        var recoverer = config.notificationDeadLetterRecoverer(mock(KafkaTemplate.class));
        var handler = config.notificationKafkaErrorHandler(recoverer);

        assertThat(handler.isAckAfterHandle()).isTrue();
        assertThat(handler.seeksAfterHandling()).isTrue();
    }
}
