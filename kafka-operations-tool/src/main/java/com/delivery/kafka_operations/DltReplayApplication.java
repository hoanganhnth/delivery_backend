package com.delivery.kafka_operations;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Operator-only recovery command for exactly one Spring Kafka DLT record.
 *
 * <p>The command intentionally has no bulk mode, defaults to dry-run and
 * requires an exact confirmation tied to the DLT topic/partition/offset. It
 * preserves the original key, payload and application headers while removing
 * recovery headers that would turn a new source delivery into a nested DLT
 * history. Broker ACLs remain the security control: this process must possess
 * both read access to the DLT and write access to the original source topic.</p>
 */
public final class DltReplayApplication {

    private DltReplayApplication() {
    }

    public static void main(String[] args) {
        if (args.length != 0) {
            System.err.println("This command is configured only through its documented environment variables.");
            System.exit(2);
        }

        try {
            DltReplayRequest request = DltReplayRequest.fromEnvironment(System.getenv());
            DltReplayResult result = new DltReplayExecutor().execute(request);
            System.out.println(result.describe());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println("DLT replay refused: " + exception.getMessage());
            System.exit(2);
        } catch (Exception exception) {
            // Never print record key/value or client configuration. Operators
            // have the incident ID and DLT coordinate needed to investigate.
            System.err.println("DLT replay failed before completion: "
                    + exception.getClass().getSimpleName());
            System.exit(1);
        }
    }
}

record DltReplayRequest(
        String bootstrapServers,
        String dltTopic,
        int dltPartition,
        long dltOffset,
        String incidentId,
        boolean dryRun,
        Path commandConfig,
        Duration pollTimeout,
        Duration sendTimeout) {

    private static final Pattern INCIDENT_ID = Pattern.compile("[A-Za-z0-9._:-]{3,128}");

    static DltReplayRequest fromEnvironment(Map<String, String> environment) {
        String bootstrapServers = required(environment, "KAFKA_BOOTSTRAP_SERVERS");
        String dltTopic = requiredTopic(environment, "DLT_REPLAY_TOPIC");
        int partition = requiredInt(environment, "DLT_REPLAY_PARTITION", 0, Integer.MAX_VALUE);
        long offset = requiredLong(environment, "DLT_REPLAY_OFFSET", 0, Long.MAX_VALUE);
        String incidentId = required(environment, "DLT_REPLAY_INCIDENT_ID");
        if (!INCIDENT_ID.matcher(incidentId).matches()) {
            throw new IllegalArgumentException("DLT_REPLAY_INCIDENT_ID must be 3-128 safe incident characters");
        }

        String expectedConfirmation = "REPLAY:" + dltTopic + ":" + partition + ":" + offset;
        if (!expectedConfirmation.equals(required(environment, "DLT_REPLAY_CONFIRMATION"))) {
            throw new IllegalArgumentException(
                    "DLT_REPLAY_CONFIRMATION must exactly equal " + expectedConfirmation);
        }

        boolean dryRun = booleanValue(environment, "DLT_REPLAY_DRY_RUN", true);
        Path commandConfig = optionalReadablePath(environment, "KAFKA_COMMAND_CONFIG");
        Duration pollTimeout = Duration.ofSeconds(requiredInt(
                environment, "DLT_REPLAY_POLL_TIMEOUT_SECONDS", 1, 60, 15));
        Duration sendTimeout = Duration.ofSeconds(requiredInt(
                environment, "DLT_REPLAY_SEND_TIMEOUT_SECONDS", 1, 60, 30));
        return new DltReplayRequest(bootstrapServers, dltTopic, partition, offset,
                incidentId, dryRun, commandConfig, pollTimeout, sendTimeout);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value;
    }

    private static String requiredTopic(Map<String, String> environment, String name) {
        String topic = required(environment, name);
        if (!DltReplayInspector.isSafeTopic(topic)) {
            throw new IllegalArgumentException(name + " is not a valid Kafka topic name");
        }
        return topic;
    }

    private static int requiredInt(Map<String, String> environment, String name, int minimum, int maximum) {
        return requiredInt(environment, name, minimum, maximum, null);
    }

    private static int requiredInt(Map<String, String> environment, String name,
                                   int minimum, int maximum, Integer defaultValue) {
        String raw = environment.get(name);
        if ((raw == null || raw.isBlank()) && defaultValue != null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(required(environment, name));
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static long requiredLong(Map<String, String> environment, String name, long minimum, long maximum) {
        try {
            long value = Long.parseLong(required(environment, name));
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a long integer", exception);
        }
    }

    private static boolean booleanValue(Map<String, String> environment, String name, boolean defaultValue) {
        String raw = environment.get(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        if ("true".equals(raw)) {
            return true;
        }
        if ("false".equals(raw)) {
            return false;
        }
        throw new IllegalArgumentException(name + " must be true or false");
    }

    private static Path optionalReadablePath(Map<String, String> environment, String name) {
        String raw = environment.get(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Path path = Path.of(raw);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException(name + " is not a readable file: " + path);
        }
        return path;
    }
}

final class DltReplayExecutor {

    DltReplayResult execute(DltReplayRequest request) throws Exception {
        Properties consumerProperties = KafkaClientProperties.consumer(request);
        TopicPartition dltPartition = new TopicPartition(request.dltTopic(), request.dltPartition());
        try (Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(consumerProperties)) {
            ConsumerRecord<byte[], byte[]> record = fetchExactRecord(consumer, dltPartition, request);
            DltReplayCandidate candidate = DltReplayInspector.inspect(record, request);
            if (request.dryRun()) {
                return DltReplayResult.dryRun(candidate, request);
            }

            try (Producer<byte[], byte[]> producer = new KafkaProducer<>(KafkaClientProperties.producer(request))) {
                RecordMetadata metadata = producer.send(candidate.toProducerRecord(request))
                        .get(request.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                return DltReplayResult.replayed(candidate, request, metadata);
            }
        }
    }

    private ConsumerRecord<byte[], byte[]> fetchExactRecord(
            Consumer<byte[], byte[]> consumer, TopicPartition dltPartition, DltReplayRequest request) {
        consumer.assign(List.of(dltPartition));
        consumer.seek(dltPartition, request.dltOffset());
        long deadlineNanos = System.nanoTime() + request.pollTimeout().toNanos();
        while (System.nanoTime() < deadlineNanos) {
            ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<byte[], byte[]> record : records.records(dltPartition)) {
                if (record.offset() == request.dltOffset()) {
                    return record;
                }
                if (record.offset() > request.dltOffset()) {
                    throw new IllegalStateException("DLT offset is no longer present in the requested partition");
                }
            }
        }
        throw new IllegalStateException("Timed out reading the exact requested DLT record");
    }
}

final class KafkaClientProperties {

    private KafkaClientProperties() {
    }

    static Properties consumer(DltReplayRequest request) throws IOException {
        Properties properties = base(request);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");
        properties.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "delivery-dlt-replay-inspect-" + UUID.randomUUID());
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, "delivery-dlt-replay-inspect");
        return properties;
    }

    static Properties producer(DltReplayRequest request) throws IOException {
        Properties properties = base(request);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, "delivery-dlt-replay-publish");
        return properties;
    }

    private static Properties base(DltReplayRequest request) throws IOException {
        Properties properties = new Properties();
        if (request.commandConfig() != null) {
            try (InputStream input = Files.newInputStream(request.commandConfig())) {
                properties.load(input);
            }
        }
        properties.put("bootstrap.servers", request.bootstrapServers());
        return properties;
    }
}

final class DltReplayInspector {

    static final String ORIGINAL_TOPIC = "kafka_dlt-original-topic";
    static final String ORIGINAL_PARTITION = "kafka_dlt-original-partition";
    static final String ORIGINAL_OFFSET = "kafka_dlt-original-offset";
    static final String EXCEPTION_FQCN = "kafka_dlt-exception-fqcn";
    static final String EVENT_ID = "eventId";
    static final String CORRELATION_ID = "correlationId";
    static final String CORRELATION_HEADER = "X-Correlation-Id";
    static final String REPLAY_HEADER_PREFIX = "delivery-dlt-replay-";

    private static final Pattern TOPIC = Pattern.compile("[A-Za-z0-9._-]{1,249}");
    private static final Pattern CORRELATION = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern EXCEPTION_CLASS = Pattern.compile("[A-Za-z0-9_.$]{1,512}");

    private DltReplayInspector() {
    }

    static DltReplayCandidate inspect(ConsumerRecord<byte[], byte[]> record, DltReplayRequest request) {
        if (!request.dltTopic().equals(record.topic()) || request.dltPartition() != record.partition()
                || request.dltOffset() != record.offset()) {
            throw new IllegalArgumentException("record coordinate does not match the approved DLT replay coordinate");
        }

        String failedTopic = requiredUtf8(record.headers(), ORIGINAL_TOPIC);
        String targetTopic = canonicalReplayTopic(failedTopic);
        if (!isSafeTopic(targetTopic) || isDltTopic(targetTopic) || targetTopic.equals(request.dltTopic())) {
            throw new IllegalArgumentException("DLT original topic is absent, unsafe, or points back to the DLT");
        }
        int originalPartition = requiredIntHeader(record.headers(), ORIGINAL_PARTITION);
        long originalOffset = requiredLongHeader(record.headers(), ORIGINAL_OFFSET);
        if (originalPartition < 0 || originalOffset < 0) {
            throw new IllegalArgumentException("DLT original partition and offset must be non-negative");
        }

        String exceptionClass = requiredUtf8(record.headers(), EXCEPTION_FQCN);
        if (!EXCEPTION_CLASS.matcher(exceptionClass).matches()) {
            throw new IllegalArgumentException("DLT exception class header is invalid");
        }
        String eventId = requiredUtf8(record.headers(), EVENT_ID);
        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("DLT eventId is not a stable UUID", exception);
        }
        String correlationId = requiredCorrelation(record.headers());
        return new DltReplayCandidate(record, failedTopic, targetTopic, originalPartition,
                originalOffset, eventId, correlationId, exceptionClass);
    }

    static boolean isSafeTopic(String topic) {
        return topic != null && TOPIC.matcher(topic).matches() && !".".equals(topic) && !"..".equals(topic);
    }

    static boolean isDltTopic(String topic) {
        return topic != null && topic.endsWith(".DLT");
    }

    /**
     * A non-blocking retry record arrives at its owner DLT with the retry topic
     * as Spring's DLT original topic. The retry topic has the same partition as
     * its canonical source, so replay must return there rather than enqueue a
     * one-off fourth retry. Only repository-standard suffixes are rewritten;
     * every other topic is replayed exactly as recorded.
     */
    static String canonicalReplayTopic(String failedTopic) {
        if (failedTopic == null) {
            return null;
        }
        return failedTopic.replaceFirst(
                "(?:-retry(?:-(?:saga|order|notification|promotion|flashsale|tracking))?-\\d+|\\.retry-\\d+)$", "");
    }

    private static String requiredCorrelation(Headers headers) {
        Optional<String> conventional = optionalUtf8(headers, CORRELATION_ID);
        Optional<String> canonical = optionalUtf8(headers, CORRELATION_HEADER);
        if (conventional.isPresent() && canonical.isPresent() && !conventional.get().equals(canonical.get())) {
            throw new IllegalArgumentException("DLT correlation headers disagree");
        }
        String value = canonical.orElseGet(() -> conventional.orElseThrow(() ->
                new IllegalArgumentException("DLT correlation identity header is missing")));
        if (!CORRELATION.matcher(value).matches()) {
            throw new IllegalArgumentException("DLT correlation identity header is invalid");
        }
        return value;
    }

    private static String requiredUtf8(Headers headers, String name) {
        return optionalUtf8(headers, name)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("DLT header " + name + " is missing or blank"));
    }

    private static Optional<String> optionalUtf8(Headers headers, String name) {
        String value = null;
        for (Header header : headers.headers(name)) {
            if (header.value() == null) {
                throw new IllegalArgumentException("DLT header " + name + " has a null value");
            }
            String decoded = new String(header.value(), StandardCharsets.UTF_8);
            if (value != null && !value.equals(decoded)) {
                throw new IllegalArgumentException("DLT header " + name + " has contradictory values");
            }
            value = decoded;
        }
        return Optional.ofNullable(value);
    }

    private static int requiredIntHeader(Headers headers, String name) {
        Header header = requiredBinaryHeader(headers, name, Integer.BYTES);
        return ByteBuffer.wrap(header.value()).getInt();
    }

    private static long requiredLongHeader(Headers headers, String name) {
        Header header = requiredBinaryHeader(headers, name, Long.BYTES);
        return ByteBuffer.wrap(header.value()).getLong();
    }

    private static Header requiredBinaryHeader(Headers headers, String name, int length) {
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null || header.value().length != length) {
            throw new IllegalArgumentException("DLT header " + name + " has an invalid binary value");
        }
        return header;
    }

    static boolean isRecoveryHeader(String name) {
        return name.startsWith("kafka_dlt-")
                || name.startsWith("kafka_exception-")
                || name.startsWith("kafka_original-")
                || name.startsWith("retry_topic-")
                || name.equals("kafka_deliveryAttempt")
                || name.startsWith(REPLAY_HEADER_PREFIX);
    }
}

record DltReplayCandidate(
        ConsumerRecord<byte[], byte[]> dltRecord,
        String failedTopic,
        String targetTopic,
        int originalPartition,
        long originalOffset,
        String eventId,
        String correlationId,
        String exceptionClass) {

    org.apache.kafka.clients.producer.ProducerRecord<byte[], byte[]> toProducerRecord(DltReplayRequest request) {
        RecordHeaders headers = new RecordHeaders();
        for (Header header : dltRecord.headers()) {
            if (!DltReplayInspector.isRecoveryHeader(header.key())) {
                headers.add(header.key(), header.value());
            }
        }
        String replayId = UUID.randomUUID().toString();
        headers.add(DltReplayInspector.REPLAY_HEADER_PREFIX + "id", bytes(replayId));
        headers.add(DltReplayInspector.REPLAY_HEADER_PREFIX + "incident-id", bytes(request.incidentId()));
        headers.add(DltReplayInspector.REPLAY_HEADER_PREFIX + "source-topic", bytes(request.dltTopic()));
        headers.add(DltReplayInspector.REPLAY_HEADER_PREFIX + "source-partition", bytes(Integer.toString(request.dltPartition())));
        headers.add(DltReplayInspector.REPLAY_HEADER_PREFIX + "source-offset", bytes(Long.toString(request.dltOffset())));
        headers.add(DltReplayInspector.REPLAY_HEADER_PREFIX + "failed-topic", bytes(failedTopic));
        return new org.apache.kafka.clients.producer.ProducerRecord<>(
                targetTopic, originalPartition, dltRecord.key(), dltRecord.value(), headers);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

record DltReplayResult(
        boolean dryRun,
        String dltTopic,
        int dltPartition,
        long dltOffset,
        String failedTopic,
        String targetTopic,
        int targetPartition,
        long originalOffset,
        String eventId,
        String correlationId,
        String incidentId,
        String exceptionClass,
        Long replayOffset) {

    static DltReplayResult dryRun(DltReplayCandidate candidate, DltReplayRequest request) {
        return create(true, candidate, request, null);
    }

    static DltReplayResult replayed(DltReplayCandidate candidate, DltReplayRequest request, RecordMetadata metadata) {
        return create(false, candidate, request, metadata.offset());
    }

    private static DltReplayResult create(boolean dryRun, DltReplayCandidate candidate,
                                          DltReplayRequest request, Long replayOffset) {
        return new DltReplayResult(dryRun, request.dltTopic(), request.dltPartition(), request.dltOffset(),
                candidate.failedTopic(), candidate.targetTopic(), candidate.originalPartition(), candidate.originalOffset(),
                candidate.eventId(), candidate.correlationId(), request.incidentId(), candidate.exceptionClass(),
                replayOffset);
    }

    String describe() {
        String prefix = dryRun ? "DRY_RUN_APPROVED" : "REPLAYED";
        String result = prefix + " incidentId=" + incidentId
                + " dlt=" + dltTopic + ":" + dltPartition + ":" + dltOffset
                + " failedTopic=" + failedTopic
                + " target=" + targetTopic + ":" + targetPartition
                + " originalOffset=" + originalOffset
                + " eventId=" + eventId
                + " correlationId=" + correlationId
                + " exception=" + exceptionClass;
        return replayOffset == null ? result : result + " replayOffset=" + replayOffset;
    }
}
