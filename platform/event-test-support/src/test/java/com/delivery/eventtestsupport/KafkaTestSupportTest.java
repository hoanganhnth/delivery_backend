package com.delivery.eventtestsupport;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaTestSupportTest {

    @Test
    void createsStringProducerAndConsumerPropertiesForEmbeddedBroker() {
        RecordingBroker recording = new RecordingBroker();
        EmbeddedKafkaBroker broker = recording.proxy();

        assertThat(KafkaTestSupport.producerProperties(broker))
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:19092")
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.StringSerializer.class);
        assertThat(KafkaTestSupport.consumerProperties(broker, "event-test-group"))
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:19092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "event-test-group")
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }

    @Test
    void delegatesTopicRegistrationAndConsumerSubscription() {
        RecordingBroker recording = new RecordingBroker();
        EmbeddedKafkaBroker broker = recording.proxy();
        org.apache.kafka.clients.consumer.Consumer<String, String> consumer = ProxyFactory.consumer();

        KafkaTestSupport.addTopics(broker, "order.created", "delivery.completed");
        KafkaTestSupport.consumeFrom(broker, consumer, "order.created");

        assertThat(recording.calls()).containsExactly(
                "addTopics:[order.created, delivery.completed]",
                "consumeFromAnEmbeddedTopic:order.created");
    }

    @Test
    void createsRecordsAndValidatesArguments() {
        assertThat(KafkaTestSupport.record("order.created", "970001", "payload"))
                .satisfies(record -> {
                    assertThat(record.topic()).isEqualTo("order.created");
                    assertThat(record.key()).isEqualTo("970001");
                    assertThat(record.value()).isEqualTo("payload");
                });
        assertThat(KafkaTestSupport.topic("order.created", 2).numPartitions()).isEqualTo(2);
        assertThatThrownBy(() -> KafkaTestSupport.startBroker(0, 1, "events"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KafkaTestSupport.consumerProperties(broker(), ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stopsNullSafelyAndValidatesAwaitArguments() {
        KafkaTestSupport.stopBroker(null);
        assertThatThrownBy(() -> KafkaTestSupport.awaitSingleRecord(null, "events", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KafkaTestSupport.addTopics(null, "events"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EmbeddedKafkaBroker broker() {
        return new RecordingBroker().proxy();
    }

    private static final class RecordingBroker implements InvocationHandler {
        private final List<String> calls = new ArrayList<>();

        EmbeddedKafkaBroker proxy() {
            return (EmbeddedKafkaBroker) Proxy.newProxyInstance(
                    EmbeddedKafkaBroker.class.getClassLoader(),
                    new Class<?>[]{EmbeddedKafkaBroker.class}, this);
        }

        List<String> calls() {
            return calls;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("getBrokersAsString")) return "127.0.0.1:19092";
            if (method.getName().equals("addTopics")) {
                calls.add("addTopics:" + java.util.Arrays.toString((String[]) args[0]));
                return null;
            }
            if (method.getName().equals("consumeFromAnEmbeddedTopic")) {
                calls.add("consumeFromAnEmbeddedTopic:" + args[1]);
                return null;
            }
            if (method.getName().equals("toString")) return "recording-broker";
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        }
    }

    private static final class ProxyFactory {
        private ProxyFactory() {
        }

        @SuppressWarnings("unchecked")
        static <K, V> org.apache.kafka.clients.consumer.Consumer<K, V> consumer() {
            return (org.apache.kafka.clients.consumer.Consumer<K, V>) Proxy.newProxyInstance(
                    org.apache.kafka.clients.consumer.Consumer.class.getClassLoader(),
                    new Class<?>[]{org.apache.kafka.clients.consumer.Consumer.class},
                    (proxy, method, args) -> null);
        }
    }
}
