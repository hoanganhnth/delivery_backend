package com.delivery.promotion_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConfigTest {

    @Test
    void usesConfiguredConsumerIdentityAndRawRetrySerializer() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "promotion-test-group", false);
        assertThat(config.consumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "promotion-test-group");
        assertThat(config.retryKafkaTemplate().getProducerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.StringSerializer.class);
    }

    @Test
    void marksTheOutboxTemplatePrimaryWhileRetryUsesItsExplicitName() throws Exception {
        assertThat(KafkaConfig.class.getDeclaredMethod("kafkaTemplate")
                .getAnnotation(Primary.class)).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryTopicFallbackReturnsToTheCanonicalPromotionDlt() {
        KafkaConfig config = new KafkaConfig("kafka:19092", "promotion-test-group", true);
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var recoverer = config.promotionDeadLetterRecoverer(template);

        recoverer.accept(new ConsumerRecord<>("order.created-retry-promotion-1000", 2, 3L,
                "91", "bad-json"), new IllegalArgumentException("poison"));

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("order.created.promotion.DLT");
        assertThat(sent.getValue().partition()).isEqualTo(2);
        assertThat(sent.getValue().value()).isEqualTo("bad-json");
    }
}
