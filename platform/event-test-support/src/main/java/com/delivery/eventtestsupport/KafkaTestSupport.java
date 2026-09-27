package com.delivery.eventtestsupport;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Small lifecycle and client helpers for Kafka integration tests. */
public final class KafkaTestSupport {

    private KafkaTestSupport() {
    }

    public static EmbeddedKafkaBroker startBroker(String... topics) {
        return startBroker(1, 1, topics);
    }

    public static EmbeddedKafkaBroker startBroker(int brokerCount, int partitions, String... topics) {
        if (brokerCount < 1 || partitions < 1) {
            throw new IllegalArgumentException("brokerCount and partitions must be positive");
        }
        EmbeddedKafkaBroker broker = new EmbeddedKafkaKraftBroker(brokerCount, partitions, topics);
        try {
            broker.afterPropertiesSet();
            return broker;
        } catch (Exception exception) {
            broker.destroy();
            throw new IllegalStateException("Could not start embedded Kafka", exception);
        }
    }

    public static void stopBroker(EmbeddedKafkaBroker broker) {
        if (broker != null) broker.destroy();
    }

    public static void addTopics(EmbeddedKafkaBroker broker, String... topics) {
        requireBroker(broker);
        if (topics == null || topics.length == 0) {
            throw new IllegalArgumentException("at least one topic is required");
        }
        broker.addTopics(topics);
    }

    public static NewTopic topic(String name, int partitions) {
        if (name == null || name.isBlank() || partitions < 1) {
            throw new IllegalArgumentException("topic name and positive partitions are required");
        }
        return new NewTopic(name, partitions, (short) 1);
    }

    public static Map<String, Object> producerProperties(EmbeddedKafkaBroker broker) {
        requireBroker(broker);
        Map<String, Object> properties = new HashMap<>(KafkaTestUtils.producerProps(broker));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return properties;
    }

    public static Map<String, Object> consumerProperties(EmbeddedKafkaBroker broker, String groupId) {
        requireBroker(broker);
        if (groupId == null || groupId.isBlank()) throw new IllegalArgumentException("groupId is required");
        Map<String, Object> properties = new HashMap<>(KafkaTestUtils.consumerProps(groupId, "true", broker));
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return properties;
    }

    public static KafkaProducer<String, String> stringProducer(EmbeddedKafkaBroker broker) {
        return new KafkaProducer<>(producerProperties(broker));
    }

    public static KafkaConsumer<String, String> stringConsumer(EmbeddedKafkaBroker broker, String groupId) {
        return new KafkaConsumer<>(consumerProperties(broker, groupId));
    }

    public static void consumeFrom(EmbeddedKafkaBroker broker, Consumer<?, ?> consumer, String topic) {
        requireBroker(broker);
        if (consumer == null || topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("consumer and topic are required");
        }
        broker.consumeFromAnEmbeddedTopic(consumer, topic);
    }

    public static <K, V> ConsumerRecord<K, V> awaitSingleRecord(Consumer<K, V> consumer,
                                                                  String topic, Duration timeout) {
        if (consumer == null || topic == null || topic.isBlank() || timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("consumer, topic and non-negative timeout are required");
        }
        return KafkaTestUtils.getSingleRecord(consumer, topic, timeout);
    }

    public static <K, V> ProducerRecord<K, V> record(String topic, K key, V value) {
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is required");
        return new ProducerRecord<>(topic, key, value);
    }

    private static void requireBroker(EmbeddedKafkaBroker broker) {
        if (broker == null) throw new IllegalArgumentException("embedded Kafka broker is required");
    }
}
