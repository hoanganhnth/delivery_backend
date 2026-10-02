package com.delivery.restaurant_service.service;

import com.delivery.restaurant.infrastructure.inventory.JsonInventoryOrderEventAdapter;
import com.delivery.restaurant.application.api.MenuItemInventoryUseCase;
import com.delivery.restaurant.application.api.RestaurantTransactionPort;
import com.delivery.restaurant.application.DefaultInventoryOrderEventUseCase;
import com.delivery.restaurant.infrastructure.inventory.JpaInventoryReceiptAdapter;
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
class JsonInventoryOrderEventAdapterTest {

    @Mock MenuItemInventoryUseCase reservationService;
    @Mock MenuItemInventoryOrderReceiptRepository receiptRepository;

    @Test
    void commitsCreatedOrderAndReleasesCancellationUsingInventoryIdentity() throws Exception {
        UUID createdEventId = UUID.randomUUID();
        UUID cancelledEventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        JsonInventoryOrderEventAdapter processor = processor();
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
        JsonInventoryOrderEventAdapter processor = processor();
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
        JsonInventoryOrderEventAdapter processor = processor();
        when(receiptRepository.insertIfAbsentPostgres(eq(eventId), eq("order.created"), eq("COMMIT"),
                eq(101L), eq(reservationId), any())).thenReturn(0);
        when(receiptRepository.findById(eventId)).thenReturn(Optional.of(receipt(
                eventId, "order.created", "COMMIT", 999L, reservationId, fingerprint(payload))));

        assertThatThrownBy(() -> processor.process(payload, "order.created"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contradictory");
        verify(reservationService, never()).commit(any(), any());
    }

    @Test
    void refundRetryUsesReleaseAndCanonicalReceiptIdentity() throws Exception {
        UUID eventId = UUID.randomUUID(), reservationId = UUID.randomUUID();
        when(receiptRepository.insertIfAbsentPostgres(eq(eventId), eq("order.refund-eligible"), eq("RELEASE"),
                eq(101L), eq(reservationId), any())).thenReturn(1);
        processor().process(payload(eventId, reservationId), "order.refund-eligible-retry-inventory-2");
        verify(reservationService).release(reservationId, 101L);
    }

    @Test
    void malformedWireFieldsAndUnexpectedTopicsNeverReachEventCore() throws Exception {
        var events = org.mockito.Mockito.mock(com.delivery.restaurant.application.api.InventoryOrderEventUseCase.class);
        var adapter = new JsonInventoryOrderEventAdapter(events, new ObjectMapper(), "order.created", "order.cancelled", "order.refund-eligible");
        String prefix = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"orderId\":101";
        for (String invalid : java.util.List.of("null", "{}", "{\"eventId\":null}", "{\"eventId\":\" \"}",
                "{\"eventId\":\"invalid\"}", prefix + ",\"inventoryReservationId\":\" \"}",
                prefix + ",\"inventoryReservationId\":\"invalid\"}", prefix.replace("101", "0") + "}",
                prefix.replace("101", "\"101\"") + "}")) {
            assertThatThrownBy(() -> adapter.process(invalid, "order.created")).isInstanceOf(IllegalArgumentException.class);
        }
        for (String topic : java.util.Arrays.asList(null, " ", "unexpected.topic")) {
            assertThatThrownBy(() -> adapter.process(prefix + "}", topic)).isInstanceOf(IllegalArgumentException.class);
        }
        org.mockito.Mockito.verifyNoInteractions(events);
    }

    private JsonInventoryOrderEventAdapter processor() {
        var events = new DefaultInventoryOrderEventUseCase(
                new JpaInventoryReceiptAdapter(receiptRepository, "jdbc:postgresql://localhost/restaurant"), reservationService,
                new RestaurantTransactionPort() {
                    public <T> T required(java.util.function.Supplier<T> operation) { return operation.get(); }
                    public <T> T repeatableRead(java.util.function.Supplier<T> operation) { return operation.get(); }
                    public <T> T readOnly(java.util.function.Supplier<T> operation) { return operation.get(); }
                });
        return new JsonInventoryOrderEventAdapter(events, new ObjectMapper(), "order.created",
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
