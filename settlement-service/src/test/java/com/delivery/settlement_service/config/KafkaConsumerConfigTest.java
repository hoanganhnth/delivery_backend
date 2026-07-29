package com.delivery.settlement_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConsumerConfigTest {

    @Test
    void usesConfiguredConsumerIdentityAndManualAck() {
        KafkaConsumerConfig config = new KafkaConsumerConfig(
                "kafka:19092", "settlement-test-group");
        var recoverer = config.settlementDeadLetterRecoverer(mock(KafkaTemplate.class));

        assertThat(config.consumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "settlement-test-group");
        assertThat(config.kafkaListenerContainerFactory(
                config.settlementKafkaErrorHandler(recoverer)).getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.MANUAL);
    }

    @Test
    @SuppressWarnings("unchecked")
    void poisonFinancialEventIsPublishedUnchangedToSamePartitionDlt() {
        KafkaConsumerConfig config = new KafkaConsumerConfig(
                "kafka:19092", "settlement-test-group");
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var recoverer = config.settlementDeadLetterRecoverer(template);
        ConsumerRecord<String, Object> source = new ConsumerRecord<>(
                "delivery.completed", 6, 12L, "order-101", "bad-json");

        recoverer.accept(source, new IllegalArgumentException("poison"));

        ArgumentCaptor<ProducerRecord<String, Object>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("delivery.completed.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(6);
        assertThat(sent.getValue().key()).isEqualTo("order-101");
        assertThat(sent.getValue().value()).isEqualTo("bad-json");
    }

    @Test
    void errorHandlerCommitsOnlyAfterFiniteRetryRecovery() {
        KafkaConsumerConfig config = new KafkaConsumerConfig(
                "kafka:19092", "settlement-test-group");
        var recoverer = config.settlementDeadLetterRecoverer(mock(KafkaTemplate.class));
        var handler = config.settlementKafkaErrorHandler(recoverer);

        assertThat(handler.isAckAfterHandle()).isTrue();
        assertThat(handler.seeksAfterHandling()).isTrue();
    }
}
