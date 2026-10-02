package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.entity.RestaurantOutboxEvent;
import com.delivery.restaurant_service.repository.RestaurantOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantOutboxRelayTest {

    @Mock RestaurantOutboxEventRepository repository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    RestaurantOutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new RestaurantOutboxRelay(repository, kafkaTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(relay, "batchSize", 10);
        ReflectionTestUtils.setField(relay, "sendTimeoutSeconds", 1L);
        ReflectionTestUtils.setField(relay, "maxAttempts", 3);
    }

    @Test
    void successfulSendMarksEventSent() {
        RestaurantOutboxEvent event = pendingEvent();
        when(repository.lockNextBatch(10)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.relayPendingEvents();

        assertThat(event.getStatus()).isEqualTo(RestaurantOutboxEvent.Status.SENT);
        assertThat(event.getSentAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void failedSendSchedulesRetryThenEventuallyMarksDead() {
        RestaurantOutboxEvent event = pendingEvent();
        when(repository.lockNextBatch(10)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        relay.relayPendingEvents();
        assertThat(event.getStatus()).isEqualTo(RestaurantOutboxEvent.Status.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(LocalDateTime.now());

        relay.relayPendingEvents();
        relay.relayPendingEvents();
        assertThat(event.getStatus()).isEqualTo(RestaurantOutboxEvent.Status.DEAD);
        assertThat(event.getAttempts()).isEqualTo(3);
    }

    private RestaurantOutboxEvent pendingEvent() {
        RestaurantOutboxEvent event = new RestaurantOutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setAggregateId("41");
        event.setEventType("CONFIRMED");
        event.setTopic("restaurant.order-confirmed");
        event.setEventKey("41");
        event.setPayload("{\"orderId\":41}");
        event.setStatus(RestaurantOutboxEvent.Status.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }
}
