package com.delivery.kafka_operations;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the operator command replays exactly one real DLT record to its original partition. */
@Testcontainers(disabledWithoutDocker = true)
class DltReplayExecutorKafkaIntegrationTest {

    private static final String SOURCE_TOPIC = "order.created";
    private static final String DLT_TOPIC = "order.created.order.DLT";
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Test
    void dryRunDoesNotPublishAndApprovedReplayPreservesOriginalKeyValueAndPartition() throws Exception {
        createTopics();
        byte[] key = "order-81".getBytes(StandardCharsets.UTF_8);
        byte[] value = ("{\"eventId\":\"" + EVENT_ID + "\"}").getBytes(StandardCharsets.UTF_8);
        try (KafkaProducer<byte[], byte[]> producer = producer()) {
            ProducerRecord<byte[], byte[]> dlt = new ProducerRecord<>(DLT_TOPIC, 1, key, value);
            // Spring's recoverer sees the final non-blocking retry topic; the
            // operator tool must return it to the canonical source instead of
            // giving it one more retry-only delivery.
            dlt.headers().add(DltReplayInspector.ORIGINAL_TOPIC,
                    "order.created-retry-order-4000".getBytes(StandardCharsets.UTF_8));
            dlt.headers().add(DltReplayInspector.ORIGINAL_PARTITION, ByteBuffer.allocate(4).putInt(1).array());
            dlt.headers().add(DltReplayInspector.ORIGINAL_OFFSET, ByteBuffer.allocate(8).putLong(61L).array());
            dlt.headers().add(DltReplayInspector.EXCEPTION_FQCN,
                    "java.lang.IllegalArgumentException".getBytes(StandardCharsets.UTF_8));
            dlt.headers().add("eventId", EVENT_ID.toString().getBytes(StandardCharsets.UTF_8));
            dlt.headers().add("X-Correlation-Id", "corr-order-81".getBytes(StandardCharsets.UTF_8));
            dlt.headers().add("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"
                    .getBytes(StandardCharsets.UTF_8));
            producer.send(dlt).get(10, TimeUnit.SECONDS);
        }

        DltReplayRequest dryRun = request(true);
        DltReplayResult preview = new DltReplayExecutor().execute(dryRun);
        assertThat(preview.dryRun()).isTrue();
        assertThat(preview.targetTopic()).isEqualTo(SOURCE_TOPIC);
        assertThat(endOffset(SOURCE_TOPIC, 1)).isZero();

        DltReplayResult replayed = new DltReplayExecutor().execute(request(false));
        assertThat(replayed.dryRun()).isFalse();
        assertThat(replayed.replayOffset()).isZero();

        try (KafkaConsumer<byte[], byte[]> consumer = consumer()) {
            TopicPartition sourcePartition = new TopicPartition(SOURCE_TOPIC, 1);
            consumer.assign(List.of(sourcePartition));
            consumer.seekToBeginning(List.of(sourcePartition));
            ConsumerRecord<byte[], byte[]> replay = awaitOne(consumer, sourcePartition);
            assertThat(replay.key()).isEqualTo(key);
            assertThat(replay.value()).isEqualTo(value);
            assertHeader(replay.headers().lastHeader("eventId"), EVENT_ID.toString());
            assertHeader(replay.headers().lastHeader("X-Correlation-Id"), "corr-order-81");
            assertHeader(replay.headers().lastHeader("traceparent"),
                    "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
            assertThat(replay.headers().lastHeader(DltReplayInspector.ORIGINAL_TOPIC)).isNull();
            assertHeader(replay.headers().lastHeader("delivery-dlt-replay-incident-id"), "INC-81");
            assertHeader(replay.headers().lastHeader("delivery-dlt-replay-source-offset"), "0");
            assertHeader(replay.headers().lastHeader("delivery-dlt-replay-failed-topic"),
                    "order.created-retry-order-4000");
        }
    }

    private DltReplayRequest request(boolean dryRun) {
        return new DltReplayRequest(KAFKA.getBootstrapServers(), DLT_TOPIC, 1, 0L,
                "INC-81", dryRun, null, Duration.ofSeconds(10), Duration.ofSeconds(10));
    }

    private void createTopics() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(SOURCE_TOPIC, 2, (short) 1),
                    new NewTopic(DLT_TOPIC, 2, (short) 1))).all().get(10, TimeUnit.SECONDS);
        }
    }

    private KafkaProducer<byte[], byte[]> producer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
                ProducerConfig.ACKS_CONFIG, "all"));
    }

    private KafkaConsumer<byte[], byte[]> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-replay-proof-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class));
    }

    private ConsumerRecord<byte[], byte[]> awaitOne(KafkaConsumer<byte[], byte[]> consumer, TopicPartition partition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(250));
            if (!records.records(partition).isEmpty()) {
                return records.records(partition).get(0);
            }
        }
        throw new AssertionError("Timed out waiting for replayed record");
    }

    private long endOffset(String topic, int partition) {
        try (KafkaConsumer<byte[], byte[]> consumer = consumer()) {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            return consumer.endOffsets(List.of(topicPartition)).get(topicPartition);
        }
    }

    private void assertHeader(Header header, String expected) {
        assertThat(header).isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo(expected);
    }
}
