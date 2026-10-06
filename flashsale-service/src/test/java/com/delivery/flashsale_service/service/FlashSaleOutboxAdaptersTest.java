package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleOutboxEvent;
import com.delivery.flashsale_service.entity.FlashSaleReservation;
import com.delivery.flashsale_service.entity.FlashSaleReservationLine;
import com.delivery.flashsale_service.repository.FlashSaleOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlashSaleOutboxAdaptersTest {
    private final FlashSaleOutboxEventRepository repository = mock(FlashSaleOutboxEventRepository.class);

    @Test
    void enqueueRetainsDeterministicIdentityTopicKeySnapshotAndSingleRowReplay() throws Exception {
        UUID reservationId = UUID.randomUUID();
        var reservation = FlashSaleReservation.builder().reservationId(reservationId).orderId(101L)
                .userId(7L).userPrincipalId(70L).restaurantId(9L).state(FlashSaleReservation.State.RESERVED)
                .expiresAt(LocalDateTime.of(2026, 1, 1, 12, 15)).build();
        reservation.getLines().add(FlashSaleReservationLine.builder().reservation(reservation)
                .flashSaleItemId(1L).menuItemId(11L).quantity(2).unitPrice(new BigDecimal("50.00")).build());
        var mapper = new ObjectMapper().findAndRegisterModules();
        var service = new FlashSaleOutboxService(repository, mapper, "flash-sale.reservation.events");
        UUID expected = UUID.nameUUIDFromBytes((reservationId + ":FLASH_SALE_RESERVATION_RESERVED")
                .getBytes(StandardCharsets.UTF_8));

        assertThat(service.enqueue(reservation)).isEqualTo(expected);

        var event = ArgumentCaptor.forClass(FlashSaleOutboxEvent.class);
        verify(repository).save(event.capture());
        var row = event.getValue();
        assertThat(row.getEventId()).isEqualTo(expected);
        assertThat(row.getEventType()).isEqualTo("FLASH_SALE_RESERVATION_RESERVED");
        assertThat(row.getAggregateId()).isEqualTo(reservationId.toString());
        assertThat(row.getAggregateType()).isEqualTo("FLASH_SALE_RESERVATION");
        assertThat(row.getTopic()).isEqualTo("flash-sale.reservation.events");
        assertThat(row.getEventKey()).isEqualTo("101");
        assertThat(row.getStatus()).isEqualTo(FlashSaleOutboxEvent.Status.PENDING);
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getCreatedAt()).isEqualTo(row.getNextAttemptAt());
        var payload = mapper.readTree(row.getPayload());
        assertThat(payload.get("reservationId").asText()).isEqualTo(reservationId.toString());
        assertThat(payload.get("eventId").asText()).isEqualTo(expected.toString());
        assertThat(payload.get("userPrincipalId").asLong()).isEqualTo(70L);
        assertThat(payload.get("orderId").asLong()).isEqualTo(101L);
        assertThat(payload.get("restaurantId").asLong()).isEqualTo(9L);
        assertThat(payload.get("state").asText()).isEqualTo("RESERVED");
        assertThat(payload.get("items").get(0).get("menuItemId").asLong()).isEqualTo(11L);
        assertThat(payload.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(payload.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("50.00");

        when(repository.existsById(expected)).thenReturn(true);
        assertThat(service.enqueue(reservation)).isEqualTo(expected);
        verify(repository, times(1)).save(any());
    }

    @Test
    void serializationFailureStillAbortsBeforeSavingOutbox() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("broken") { });
        var reservation = FlashSaleReservation.builder().reservationId(UUID.randomUUID()).orderId(1L)
                .state(FlashSaleReservation.State.COMMITTED).build();
        assertThatThrownBy(() -> new FlashSaleOutboxService(repository, mapper, "topic").enqueue(reservation))
                .hasMessage("Flash-sale event is not serializable").hasCauseInstanceOf(JsonProcessingException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void relayKeepsLockedBoundedBatchKafkaEnvelopeAndManagedRowMutations() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var sent = event("sent", 0);
        var retry = event("retry", 0);
        var dead = event("dead", 11);
        when(repository.lockDue(eq(FlashSaleOutboxEvent.Status.PENDING), any(), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(sent, retry, dead));
        when(kafka.send("topic", "sent", "payload")).thenReturn(CompletableFuture.completedFuture(null));
        when(kafka.send("topic", "retry", "payload"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("temporary")));
        when(kafka.send("topic", "dead", "payload"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("permanent")));
        LocalDateTime before = LocalDateTime.now();

        new FlashSaleOutboxRelay(repository, kafka).relay();

        assertThat(sent.getStatus()).isEqualTo(FlashSaleOutboxEvent.Status.SENT);
        assertThat(sent.getSentAt()).isBetween(before, LocalDateTime.now());
        assertThat(sent.getLastError()).isNull();
        assertThat(retry.getStatus()).isEqualTo(FlashSaleOutboxEvent.Status.PENDING);
        assertThat(retry.getAttempts()).isEqualTo(1);
        assertThat(retry.getLastError()).contains("temporary");
        assertThat(retry.getNextAttemptAt()).isBetween(before.plusSeconds(2), LocalDateTime.now().plusSeconds(2));
        assertThat(dead.getStatus()).isEqualTo(FlashSaleOutboxEvent.Status.DEAD);
        assertThat(dead.getAttempts()).isEqualTo(12);
        assertThat(dead.getLastError()).contains("permanent");
        verify(kafka).send("topic", "sent", "payload");
        verify(kafka).send("topic", "retry", "payload");
        verify(kafka).send("topic", "dead", "payload");
        verify(repository, never()).save(any());
    }

    private FlashSaleOutboxEvent event(String key, int attempts) {
        var event = new FlashSaleOutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setTopic("topic");
        event.setEventKey(key);
        event.setPayload("payload");
        event.setStatus(FlashSaleOutboxEvent.Status.PENDING);
        event.setAttempts(attempts);
        event.setLastError("previous failure");
        return event;
    }
}
