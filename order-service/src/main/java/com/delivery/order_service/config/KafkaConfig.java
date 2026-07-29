package com.delivery.order_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.springframework.util.backoff.FixedBackOff;

/**
 * ✅ Kafka Configuration cho Order Service
 * Producer for OrderCreatedEvent + Consumer for ShipperNotFoundEvent
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    private final String bootstrapServers;
    private final String groupId;
    private final boolean listenerAutoStartup;

    @Autowired
    public KafkaConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:order-service-group}") String groupId,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean listenerAutoStartup) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.listenerAutoStartup = listenerAutoStartup;
    }

    KafkaConfig(String bootstrapServers) {
        this(bootstrapServers, "order-service-group", true);
    }

    // === PRODUCER CONFIGURATION ===

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public DeadLetterPublishingRecoverer orderDeadLetterRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    @Bean
    public DefaultErrorHandler orderKafkaErrorHandler(
            DeadLetterPublishingRecoverer recoverer) {
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
        handler.setCommitRecovered(true);
        return handler;
    }

    // === CONSUMER CONFIGURATION ===

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ObjectMapper objectMapper,
            DefaultErrorHandler orderKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setAutoStartup(listenerAutoStartup);
        // Enable manual acknowledgment so listeners can inject Acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(orderKafkaErrorHandler);
        // ✅ Convert JSON String value → POJO @Payload (Payment/Restaurant listeners).
        //    Dùng ObjectMapper của Spring (đã có JavaTimeModule cho LocalDateTime).
        //    Listener nhận String (Saga) vẫn pass-through bình thường.
        factory.setRecordMessageConverter(new StringJsonMessageConverter(objectMapper));
        return factory;
    }
}
