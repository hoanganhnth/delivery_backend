package com.delivery.tracking_service.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/** Owns Kafka templates and recovery policies for Tracking's owned consumers. */
@Configuration
public class LocationHistoryKafkaConfig {

    private final String bootstrapServers;
    private final String groupId;
    private final boolean listenerAutoStartup;

    public LocationHistoryKafkaConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${app.kafka.groups.location-history:tracking-location-history}") String groupId,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean listenerAutoStartup) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.listenerAutoStartup = listenerAutoStartup;
    }

    @Bean("locationHistoryConsumerFactory")
    public DefaultKafkaConsumerFactory<String, String> locationHistoryConsumerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    /** Retry/DLT records are raw JSON; StringSerializer avoids JSON re-quoting. */
    @Bean("trackingRetryKafkaTemplate")
    public KafkaTemplate<String, String> trackingRetryKafkaTemplate() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.RETRIES_CONFIG, 3);
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));
    }

    /**
     * Defining the raw-string recovery template makes Spring Boot back off its
     * generic KafkaTemplate auto-configuration. Keep the normal location
     * publisher independently available, with the established JSON contract,
     * instead of accidentally removing realtime fan-out at startup.
     */
    @Bean("trackingKafkaTemplate")
    @Primary
    public KafkaTemplate<String, Object> trackingKafkaTemplate() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.RETRIES_CONFIG, 3);
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));
    }

    @Bean("locationHistoryKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> locationHistoryKafkaListenerContainerFactory(
            @Qualifier("locationHistoryConsumerFactory") DefaultKafkaConsumerFactory<String, String> consumerFactory,
            @Qualifier("locationHistoryKafkaErrorHandler") DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAutoStartup(listenerAutoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    /** Routing is rebuildable, but malformed status facts must not be ACKed away. */
    @Bean("deliveryRoomsKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> deliveryRoomsKafkaListenerContainerFactory(
            @Qualifier("deliveryRoomsConsumerFactory") DefaultKafkaConsumerFactory<String, String> consumerFactory,
            @Qualifier("deliveryRoomsKafkaErrorHandler") DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAutoStartup(listenerAutoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean("deliveryRoomsConsumerFactory")
    public DefaultKafkaConsumerFactory<String, String> deliveryRoomsConsumerFactory(
            @Value("${app.kafka.groups.delivery-rooms:tracking-delivery-rooms}") String deliveryRoomsGroupId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, deliveryRoomsGroupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean("locationHistoryDeadLetterRecoverer")
    public DeadLetterPublishingRecoverer locationHistoryDeadLetterRecoverer(
            @Qualifier("trackingRetryKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(ownerDltTopic(record.topic()), record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    @Bean("locationHistoryKafkaErrorHandler")
    public DefaultErrorHandler locationHistoryKafkaErrorHandler(
            @Qualifier("locationHistoryDeadLetterRecoverer") DeadLetterPublishingRecoverer recoverer,
            MeterRegistry meterRegistry) {
        DefaultErrorHandler handler = new DefaultErrorHandler((record, error) -> {
            meterRegistry.counter("delivery.kafka.events", "event", "dlt").increment();
            recoverer.accept(record, error);
        }, new FixedBackOff(1000L, 2));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setRetryListeners(new org.springframework.kafka.listener.RetryListener() {
            @Override public void failedDelivery(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                                                 Exception error, int attempt) {
                meterRegistry.counter("delivery.kafka.events", "event", "retry").increment();
            }
        });
        handler.setCommitRecovered(true);
        return handler;
    }

    @Bean("deliveryRoomsDeadLetterRecoverer")
    public DeadLetterPublishingRecoverer deliveryRoomsDeadLetterRecoverer(
            @Qualifier("trackingRetryKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".tracking.DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    @Bean("deliveryRoomsKafkaErrorHandler")
    public DefaultErrorHandler deliveryRoomsKafkaErrorHandler(
            @Qualifier("deliveryRoomsDeadLetterRecoverer") DeadLetterPublishingRecoverer recoverer,
            MeterRegistry meterRegistry) {
        DefaultErrorHandler handler = new DefaultErrorHandler((record, error) -> {
            meterRegistry.counter("delivery.kafka.events", "event", "dlt").increment();
            recoverer.accept(record, error);
        }, new FixedBackOff(1000L, 2));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setRetryListeners(new org.springframework.kafka.listener.RetryListener() {
            @Override public void failedDelivery(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                                                 Exception error, int attempt) {
                meterRegistry.counter("delivery.kafka.events", "event", "retry").increment();
            }
        });
        handler.setCommitRecovered(true);
        return handler;
    }

    // Package-visible for configuration tests.
    static String ownerDltTopic(String topic) {
        return topic.replaceFirst("-retry-tracking-\\d+$", "") + ".tracking.DLT";
    }
}
