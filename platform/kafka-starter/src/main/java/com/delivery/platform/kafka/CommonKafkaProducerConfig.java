package com.delivery.platform.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Shared producer wiring. Services choose their own topics and keys; this
 * configuration only standardizes the typed JSON transport and reliability
 * defaults used by the existing service configurations.
 */
@AutoConfiguration(before = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class CommonKafkaProducerConfig {

    private final String bootstrapServers;

    public CommonKafkaProducerConfig() {
        this("localhost:9092");
    }

    @Autowired
    public CommonKafkaProducerConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean(name = {"commonKafkaProducerFactory", "producerFactory"})
    @ConditionalOnMissingBean(ProducerFactory.class)
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.RETRIES_CONFIG, 3);
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean(name = {"commonKafkaTemplate", "kafkaTemplate"})
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
