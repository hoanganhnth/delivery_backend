package com.delivery.order_service.service;

import com.delivery.order_service.entity.OutboxEvent;
import com.delivery.order_service.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderOutboxRelayTest {

    @Mock OutboxEventRepository repository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    private OrderOutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OrderOutboxRelay(repository, kafkaTemplate, new ObjectMapper(), 50, 2, 10);
    }

    @Test
    void successfulSendMarksEventSentAndForwardsStableMetadata() {
        OutboxEvent event = pendingEvent();
        when(repository.lockNextBatch(50)).thenReturn(List.of(event));
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, Object>>any()))
                .thenReturn(CompletableFuture.completedFuture(org.mockito.Mockito.mock(SendResult.class)));

        relay.relayPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.SENT);
        assertThat(event.getSentAt()).isNotNull();
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("order.created");
        assertThat(record.key()).isEqualTo("42");
        assertThat(new String(record.headers().lastHeader("eventId").value())).isEqualTo(event.getEventId().toString());
        assertThat(new String(record.headers().lastHeader("X-Correlation-Id").value()))
                .isEqualTo("gateway-order-42");
        verify(repository).save(event);
    }

    @Test
    void failedSendRemainsPendingAndSchedulesBackoff() {
        OutboxEvent event = pendingEvent();
        when(repository.lockNextBatch(50)).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, Object>>any()))
                .thenReturn(failed);

        relay.relayPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(LocalDateTime.now().minusSeconds(1));
        assertThat(event.getLastError()).contains("broker unavailable");
        verify(repository).save(event);
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setAggregateType("ORDER");
        event.setAggregateId("42");
        event.setEventType("ORDER_CREATED");
        event.setTopic("order.created");
        event.setEventKey("42");
        event.setPayload("{\"orderId\":42}");
        event.setCorrelationId("gateway-order-42");
        event.setStatus(OutboxEvent.Status.PENDING);
        event.setNextAttemptAt(LocalDateTime.now());
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }
}
