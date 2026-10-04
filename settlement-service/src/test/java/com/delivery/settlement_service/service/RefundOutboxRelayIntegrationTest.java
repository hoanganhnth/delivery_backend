package com.delivery.settlement_service.service;

import com.delivery.settlement_service.entity.RefundOutboxEvent;
import com.delivery.settlement_service.repository.RefundOutboxEventRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RefundOutboxRelayIntegrationTest {
    private static final String TOPIC = "refund.requested";
    private static final String KEY = "refund-key";
    private static final String PAYLOAD = "{\"refundId\":\"refund\"}";

    @Autowired RefundOutboxEventRepository events;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void clearBefore() {
        events.deleteAll();
    }

    @AfterEach
    void clearAfter() {
        events.deleteAll();
    }

    @Test
    void successfulPublishPersistsSentAndASecondScanDoesNotRepublish() throws Exception {
        var row = seed(3, LocalDateTime.now().minusMinutes(1));
        row.setLastError("previous transient failure");
        row = events.saveAndFlush(row);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(10, TimeUnit.SECONDS)).thenReturn(null);
        when(kafka.send(TOPIC, KEY, PAYLOAD)).thenReturn(future);
        RefundOutboxRelay relay = relay(kafka);

        inTransaction(() -> relay.relay());

        RefundOutboxEvent sent = events.findById(row.getEventId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(RefundOutboxEvent.Status.SENT);
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(sent.getLastError()).isNull();
        assertThat(sent.getAttempts()).isEqualTo(3);
        verify(kafka).send(TOPIC, KEY, PAYLOAD);
        verify(future).get(10, TimeUnit.SECONDS);

        inTransaction(() -> relay.relay());

        verify(kafka, times(1)).send(TOPIC, KEY, PAYLOAD);
        assertThat(events.findById(row.getEventId()).orElseThrow().getStatus())
                .isEqualTo(RefundOutboxEvent.Status.SENT);
    }

    @Test
    void failedPublishesPersistRetryAndDeadAndContinueScanningOtherRows() throws Exception {
        var retry = seed(0, LocalDateTime.now().minusMinutes(1));
        var dead = seed(11, LocalDateTime.now().minusMinutes(1));
        var success = seed(0, LocalDateTime.now().minusMinutes(1), "other-refund-key", "{\"refundId\":\"other\"}");
        String longError = "x".repeat(2500);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException(longError));
        CompletableFuture<SendResult<String, String>> completed = mock(CompletableFuture.class);
        when(completed.get(10, TimeUnit.SECONDS)).thenReturn(null);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(TOPIC, KEY, PAYLOAD)).thenReturn(failed);
        when(kafka.send(TOPIC, "other-refund-key", "{\"refundId\":\"other\"}")).thenReturn(completed);

        inTransaction(() -> relay(kafka).relay());

        RefundOutboxEvent retryAfter = events.findById(retry.getEventId()).orElseThrow();
        assertThat(retryAfter.getStatus()).isEqualTo(RefundOutboxEvent.Status.PENDING);
        assertThat(retryAfter.getAttempts()).isEqualTo(1);
        assertThat(retryAfter.getNextAttemptAt()).isAfter(retry.getNextAttemptAt());
        assertThat(retryAfter.getLastError()).hasSize(2000);

        RefundOutboxEvent deadAfter = events.findById(dead.getEventId()).orElseThrow();
        assertThat(deadAfter.getStatus()).isEqualTo(RefundOutboxEvent.Status.DEAD);
        assertThat(deadAfter.getAttempts()).isEqualTo(12);
        assertThat(deadAfter.getNextAttemptAt()).isEqualTo(dead.getNextAttemptAt());
        assertThat(deadAfter.getLastError()).hasSize(2000);
        assertThat(events.findById(success.getEventId()).orElseThrow().getStatus())
                .isEqualTo(RefundOutboxEvent.Status.SENT);
        verify(kafka, times(2)).send(TOPIC, KEY, PAYLOAD);
        verify(kafka).send(TOPIC, "other-refund-key", "{\"refundId\":\"other\"}");
    }

    @Test
    void successfulPublishRolledBackByCallerRemainsPendingForReplay() throws Exception {
        var row = seed(0, LocalDateTime.now().minusMinutes(1));
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(TOPIC, KEY, PAYLOAD))
                .thenReturn(CompletableFuture.completedFuture(null));
        RefundOutboxRelay relay = relay(kafka);

        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            relay.relay();
            throw new IllegalStateException("crash after publish before commit");
        })).isInstanceOf(IllegalStateException.class);

        RefundOutboxEvent rolledBack = events.findById(row.getEventId()).orElseThrow();
        assertThat(rolledBack.getStatus()).isEqualTo(RefundOutboxEvent.Status.PENDING);
        assertThat(rolledBack.getSentAt()).isNull();

        inTransaction(() -> relay.relay());

        assertThat(events.findById(row.getEventId()).orElseThrow().getStatus())
                .isEqualTo(RefundOutboxEvent.Status.SENT);
        verify(kafka, times(2)).send(TOPIC, KEY, PAYLOAD);
    }

    private RefundOutboxRelay relay(KafkaTemplate<String, String> kafka) {
        return new RefundOutboxRelay(events, kafka);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> work.run());
    }

    private RefundOutboxEvent seed(int attempts, LocalDateTime nextAttemptAt) {
        return seed(attempts, nextAttemptAt, KEY, PAYLOAD);
    }

    private RefundOutboxEvent seed(int attempts, LocalDateTime nextAttemptAt, String key, String payload) {
        RefundOutboxEvent row = new RefundOutboxEvent();
        row.setEventId(UUID.randomUUID());
        row.setAggregateType("REFUND_CASE");
        row.setAggregateId(UUID.randomUUID().toString());
        row.setEventType("REFUND_REQUESTED");
        row.setTopic(TOPIC);
        row.setEventKey(key);
        row.setPayload(payload);
        row.setStatus(RefundOutboxEvent.Status.PENDING);
        row.setAttempts(attempts);
        row.setNextAttemptAt(nextAttemptAt);
        row.setCreatedAt(LocalDateTime.now().minusMinutes(2).plusNanos(attempts));
        return events.saveAndFlush(row);
    }
}
