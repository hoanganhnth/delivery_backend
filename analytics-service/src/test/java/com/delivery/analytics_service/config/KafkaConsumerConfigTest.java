package com.delivery.analytics_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    @Test
    void usesConfiguredBootstrapServers() {
        KafkaConsumerConfig config = new KafkaConsumerConfig("kafka:19092");

        assertThat(config.consumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092");
    }
}
