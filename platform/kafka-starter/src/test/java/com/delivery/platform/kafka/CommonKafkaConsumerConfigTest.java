package com.delivery.platform.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.util.ReflectionTestUtils;

class CommonKafkaConsumerConfigTest {

    @Test
    void createsTypedJsonConsumerWithConfiguredGroupAndTrustedPackages() {
        CommonKafkaConsumerConfig config = new CommonKafkaConsumerConfig(
                "kafka:19092", "orders-consumer", "com.delivery.orders,java.util", false);

        assertThat(config.consumerFactory().getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:19092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "orders-consumer")
                .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class)
                .containsEntry(JsonDeserializer.TRUSTED_PACKAGES, "com.delivery.orders,java.util")
                .containsEntry(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
    }

    @Test
    void configuresManualListenerFactoryAndCommonErrorHandler() {
        CommonKafkaConsumerConfig config = new CommonKafkaConsumerConfig(
                "localhost:9092", "orders-consumer", "com.delivery", false);
        ConsumerFactory<String, Object> consumerFactory = config.consumerFactory();
        CommonKafkaErrorHandler errorHandler = new CommonKafkaErrorHandler(new TestKafkaTemplate(false));

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertThat(factory.getConsumerFactory()).isSameAs(consumerFactory);
        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        assertThat(ReflectionTestUtils.getField(factory, "commonErrorHandler")).isSameAs(errorHandler);
    }
}
