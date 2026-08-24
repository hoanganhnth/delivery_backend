package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.entity.MenuItemInventoryOrderReceipt;
import com.delivery.restaurant_service.repository.MenuItemInventoryOrderReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuItemInventoryOrderEventProcessorTest {

    @Mock MenuItemInventoryReservationService reservationService;
    @Mock MenuItemInventoryOrderReceiptRepository receiptRepository;

    @Test
    void commitsCreatedOrderAndReleasesCancellationUsingInventoryIdentity() throws Exception {
        UUID createdEventId = UUID.randomUUID();
        UUID cancelledEventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        MenuItemInventoryOrderEventProcessor processor = processor();
        when(receiptRepository.insertIfAbsentPostgres(eq(createdEventId), eq("order.created"), eq("COMMIT"),
                eq(101L), eq(reservationId), any())).thenReturn(1);
        when(receiptRepository.insertIfAbsentPostgres(eq(cancelledEventId), eq("order.cancelled"), eq("RELEASE"),
                eq(101L), eq(reservationId), any())).thenReturn(1);

        processor.process(payload(createdEventId, reservationId), "order.created");
        processor.process(payload(cancelledEventId, reservationId), "order.cancelled");

        verify(reservationService).commit(reservationId, 101L);
        verify(reservationService).release(reservationId, 101L);
    }

    @Test
    void exactReplayIsAckableWithoutAnotherInventoryTransition() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String payload = payload(eventId, reservationId);
        MenuItemInventoryOrderEventProcessor processor = processor();
        when(receiptRepository.insertIfAbsentPostgres(eq(eventId), eq("order.created"), eq("COMMIT"),
                eq(101L), eq(reservationId), any())).thenReturn(0);
        when(receiptRepository.findById(eventId)).thenReturn(Optional.of(receipt(
                eventId, "order.created", "COMMIT", 101L, reservationId, fingerprint(payload))));

        processor.process(payload, "order.created");

        verify(reservationService, never()).commit(any(), any());
        verify(reservationService, never()).release(any(), any());
    }

    @Test
    void contradictoryReplayFailsClosedBeforeMutatingInventory() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String payload = payload(eventId, reservationId);
        MenuItemInventoryOrderEventProcessor processor = processor();
        when(receiptRepository.insertIfAbsentPostgres(eq(eventId), eq("order.created"), eq("COMMIT"),
                eq(101L), eq(reservationId), any())).thenReturn(0);
        when(receiptRepository.findById(eventId)).thenReturn(Optional.of(receipt(
                eventId, "order.created", "COMMIT", 999L, reservationId, fingerprint(payload))));

        assertThatThrownBy(() -> processor.process(payload, "order.created"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contradictory");
        verify(reservationService, never()).commit(any(), any());
    }

    private MenuItemInventoryOrderEventProcessor processor() {
        return new MenuItemInventoryOrderEventProcessor(reservationService, receiptRepository,
                new ObjectMapper(), "jdbc:postgresql://localhost/restaurant", "order.created",
                "order.cancelled", "order.refund-eligible");
    }

    private String payload(UUID eventId, UUID reservationId) {
        return "{\"eventId\":\"" + eventId + "\",\"orderId\":101,"
                + "\"inventoryReservationId\":\"" + reservationId + "\"}";
    }

    private MenuItemInventoryOrderReceipt receipt(UUID eventId, String topic, String action, long orderId,
                                                  UUID reservationId, String fingerprint) {
        return MenuItemInventoryOrderReceipt.builder()
                .eventId(eventId).sourceTopic(topic).action(action).orderId(orderId)
                .reservationId(reservationId).payloadFingerprint(fingerprint)
                .createdAt(LocalDateTime.now()).build();
    }

    private String fingerprint(String payload) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
