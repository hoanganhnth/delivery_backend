package com.delivery.platform.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * Shared consumer wiring for listeners that consume the producer's typed JSON
 * records. Consumer group and trusted-package policy remain properties so
 * each service can keep ownership of its subscription boundary.
 */
@AutoConfiguration(before = KafkaAutoConfiguration.class, after = CommonKafkaProducerConfig.class)
@AutoConfigureAfter(CommonKafkaProducerConfig.class)
@ConditionalOnClass(ConcurrentKafkaListenerContainerFactory.class)
@EnableKafka
public class CommonKafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers = "localhost:9092";

    @Value("${spring.kafka.consumer.group-id:delivery-kafka-consumer}")
    private String groupId = "delivery-kafka-consumer";

    @Value("${spring.kafka.consumer.properties.spring.json.trusted.packages:com.delivery,java.util,java.lang}")
    private String trustedPackages = "com.delivery,java.util,java.lang";

    @Value("${spring.kafka.listener.auto-startup:true}")
    private boolean listenerAutoStartup = true;

    public CommonKafkaConsumerConfig() {
        this("localhost:9092", "delivery-kafka-consumer", "com.delivery,java.util,java.lang", true);
    }

    public CommonKafkaConsumerConfig(String bootstrapServers, String groupId, String trustedPackages) {
        this(bootstrapServers, groupId, trustedPackages, true);
    }

    public CommonKafkaConsumerConfig(String bootstrapServers, String groupId,
            String trustedPackages, boolean listenerAutoStartup) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.trustedPackages = trustedPackages;
        this.listenerAutoStartup = listenerAutoStartup;
    }

    @Bean(name = {"commonKafkaConsumerFactory", "consumerFactory"})
    @ConditionalOnMissingBean(ConsumerFactory.class)
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages);
        properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
        properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Object.class.getName());
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    @ConditionalOnMissingBean(CommonKafkaErrorHandler.class)
    public CommonKafkaErrorHandler commonKafkaErrorHandler(
            @Qualifier("commonKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${platform.kafka.dlt.suffix:.DLT}") String dltSuffix) {
        return new CommonKafkaErrorHandler(kafkaTemplate, dltSuffix);
    }

    @Bean(name = {"commonKafkaListenerContainerFactory", "kafkaListenerContainerFactory"})
    @ConditionalOnMissingBean(ConcurrentKafkaListenerContainerFactory.class)
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            @Qualifier("commonKafkaConsumerFactory") ConsumerFactory<String, Object> consumerFactory,
            CommonKafkaErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAutoStartup(listenerAutoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
