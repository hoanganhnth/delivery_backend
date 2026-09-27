package com.delivery.platform.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

class CommonKafkaProducerConfigTest {

    @Test
    void createsTypedJsonProducerForConfiguredBootstrapServers() {
        CommonKafkaProducerConfig config = new CommonKafkaProducerConfig("kafka:19092");

        assertThat(config.producerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092")
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    }

    @Test
    void exposesKafkaTemplateWithObjectValueType() {
        CommonKafkaProducerConfig config = new CommonKafkaProducerConfig("localhost:9092");

        assertThat(config.kafkaTemplate()).isInstanceOf(KafkaTemplate.class);
        assertThat(config.kafkaTemplate().getProducerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    }
}
