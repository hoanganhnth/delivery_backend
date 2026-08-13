package com.delivery.kafka_operations;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DltReplayInspectorTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void acceptsOneFullyIdentifiedRecordAndPreservesOnlyApplicationHeaders() {
        ConsumerRecord<byte[], byte[]> dlt = validRecord();
        dlt.headers().add("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"
                .getBytes(StandardCharsets.UTF_8));
        dlt.headers().add("retry_topic-attempts", new byte[] {1});
        DltReplayRequest request = request(false);

        DltReplayCandidate candidate = DltReplayInspector.inspect(dlt, request);
        var replay = candidate.toProducerRecord(request);

        assertThat(candidate.failedTopic()).isEqualTo("order.created");
        assertThat(candidate.targetTopic()).isEqualTo("order.created");
        assertThat(candidate.originalPartition()).isEqualTo(2);
        assertThat(candidate.originalOffset()).isEqualTo(41L);
        assertThat(replay.topic()).isEqualTo("order.created");
        assertThat(replay.partition()).isEqualTo(2);
        assertThat(replay.key()).isEqualTo("order-41".getBytes(StandardCharsets.UTF_8));
        assertThat(replay.value()).isEqualTo(("{\"eventId\":\"" + EVENT_ID + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        assertThat(replay.headers().lastHeader(DltReplayInspector.ORIGINAL_TOPIC)).isNull();
        assertThat(replay.headers().lastHeader("retry_topic-attempts")).isNull();
        assertHeader(replay.headers().lastHeader("eventId"), EVENT_ID.toString());
        assertHeader(replay.headers().lastHeader("X-Correlation-Id"), "corr-order-41");
        assertHeader(replay.headers().lastHeader("traceparent"),
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        assertHeader(replay.headers().lastHeader("delivery-dlt-replay-incident-id"), "INC-41");
    }

    @Test
    void refusesMissingOrConflictingReplayIdentity() {
        ConsumerRecord<byte[], byte[]> missingEventId = validRecord();
        missingEventId.headers().remove("eventId");
        assertThatThrownBy(() -> DltReplayInspector.inspect(missingEventId, request(true)))
                .hasMessageContaining("eventId");

        ConsumerRecord<byte[], byte[]> conflictingCorrelation = validRecord();
        conflictingCorrelation.headers().add("correlationId", "different".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> DltReplayInspector.inspect(conflictingCorrelation, request(true)))
                .hasMessageContaining("correlation headers disagree");

        ConsumerRecord<byte[], byte[]> conflictingEventId = validRecord();
        conflictingEventId.headers().add("eventId", "33333333-3333-3333-3333-333333333333"
                .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> DltReplayInspector.inspect(conflictingEventId, request(true)))
                .hasMessageContaining("eventId has contradictory values");
    }

    @Test
    void refusesMalformedOrDangerousOriginalCoordinates() {
        ConsumerRecord<byte[], byte[]> malformedPartition = validRecord();
        malformedPartition.headers().remove(DltReplayInspector.ORIGINAL_PARTITION);
        malformedPartition.headers().add(DltReplayInspector.ORIGINAL_PARTITION, "2".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> DltReplayInspector.inspect(malformedPartition, request(true)))
                .hasMessageContaining("invalid binary value");

        ConsumerRecord<byte[], byte[]> loop = validRecord();
        loop.headers().remove(DltReplayInspector.ORIGINAL_TOPIC);
        loop.headers().add(DltReplayInspector.ORIGINAL_TOPIC, "order.created.order.DLT".getBytes(StandardCharsets.UTF_8));
        DltReplayRequest loopRequest = new DltReplayRequest("kafka:9092", "order.created.order.DLT", 1, 9,
                "INC-41", true, null, Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertThatThrownBy(() -> DltReplayInspector.inspect(loop, loopRequest))
                .hasMessageContaining("points back to the DLT");

        ConsumerRecord<byte[], byte[]> otherDlt = validRecord();
        otherDlt.headers().remove(DltReplayInspector.ORIGINAL_TOPIC);
        otherDlt.headers().add(DltReplayInspector.ORIGINAL_TOPIC,
                "order.created.saga.DLT".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> DltReplayInspector.inspect(otherDlt, request(true)))
                .hasMessageContaining("points back to the DLT");

        ConsumerRecord<byte[], byte[]> unsafeException = validRecord();
        unsafeException.headers().remove(DltReplayInspector.EXCEPTION_FQCN);
        unsafeException.headers().add(DltReplayInspector.EXCEPTION_FQCN,
                "java.lang.IllegalArgumentException\nforged-log".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> DltReplayInspector.inspect(unsafeException, request(true)))
                .hasMessageContaining("exception class header is invalid");
    }

    @Test
    void canonicalizesOnlyTheKnownRetryTopicSuffixesBackToTheirSource() {
        assertThat(DltReplayInspector.canonicalReplayTopic("order.created-retry-saga-4000"))
                .isEqualTo("order.created");
        assertThat(DltReplayInspector.canonicalReplayTopic("order.created-retry-1000"))
                .isEqualTo("order.created");
        assertThat(DltReplayInspector.canonicalReplayTopic("saga.command.find-shipper.retry-2000"))
                .isEqualTo("saga.command.find-shipper");
        assertThat(DltReplayInspector.canonicalReplayTopic("externally-named-retry-topic"))
                .isEqualTo("externally-named-retry-topic");
    }

    @Test
    void environmentDefaultsToDryRunAndRequiresExactCoordinateConfirmation() {
        Map<String, String> environment = Map.of(
                "KAFKA_BOOTSTRAP_SERVERS", "kafka:9092",
                "DLT_REPLAY_TOPIC", "order.created.order.DLT",
                "DLT_REPLAY_PARTITION", "1",
                "DLT_REPLAY_OFFSET", "9",
                "DLT_REPLAY_INCIDENT_ID", "INC-41",
                "DLT_REPLAY_CONFIRMATION", "REPLAY:order.created.order.DLT:1:9");

        assertThat(DltReplayRequest.fromEnvironment(environment).dryRun()).isTrue();

        assertThatThrownBy(() -> DltReplayRequest.fromEnvironment(Map.of(
                "KAFKA_BOOTSTRAP_SERVERS", "kafka:9092",
                "DLT_REPLAY_TOPIC", "order.created.order.DLT",
                "DLT_REPLAY_PARTITION", "1",
                "DLT_REPLAY_OFFSET", "9",
                "DLT_REPLAY_INCIDENT_ID", "INC-41",
                "DLT_REPLAY_CONFIRMATION", "REPLAY:order.created.order.DLT:1:8")))
                .hasMessageContaining("DLT_REPLAY_CONFIRMATION");
    }

    private ConsumerRecord<byte[], byte[]> validRecord() {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("order.created.order.DLT", 1, 9L,
                "order-41".getBytes(StandardCharsets.UTF_8),
                ("{\"eventId\":\"" + EVENT_ID + "\"}").getBytes(StandardCharsets.UTF_8));
        record.headers().add(DltReplayInspector.ORIGINAL_TOPIC, "order.created".getBytes(StandardCharsets.UTF_8));
        record.headers().add(DltReplayInspector.ORIGINAL_PARTITION, ByteBuffer.allocate(4).putInt(2).array());
        record.headers().add(DltReplayInspector.ORIGINAL_OFFSET, ByteBuffer.allocate(8).putLong(41L).array());
        record.headers().add(DltReplayInspector.EXCEPTION_FQCN,
                "java.lang.IllegalArgumentException".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventId", EVENT_ID.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("X-Correlation-Id", "corr-order-41".getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private DltReplayRequest request(boolean dryRun) {
        return new DltReplayRequest("kafka:9092", "order.created.order.DLT", 1, 9L,
                "INC-41", dryRun, Path.of("/dev/null"), Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private void assertHeader(Header header, String expected) {
        assertThat(header).isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo(expected);
    }
}
