package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleOrderReservationReceipt;
import com.delivery.flashsale_service.repository.FlashSaleOrderReservationReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlashSaleOrderReservationEventProcessorTest {

    private final FlashSaleStockService stockService = mock(FlashSaleStockService.class);
    private final FlashSaleOrderReservationReceiptRepository receipts =
            mock(FlashSaleOrderReservationReceiptRepository.class);
    private final FlashSaleOrderReservationEventProcessor processor =
            new FlashSaleOrderReservationEventProcessor(
                    stockService, receipts, new ObjectMapper(), "jdbc:postgresql://db/flashsale",
                    "order.created", "order.cancelled", "order.refund-eligible");

    @Test
    void firstCreatedEventClaimsReceiptThenCommitsTheBoundReservation() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String payload = payload(eventId, 91L, reservationId);
        when(receipts.insertIfAbsentPostgres(eq(eventId), eq("order.created"), eq("COMMIT"), eq(91L),
                eq(reservationId), anyString())).thenReturn(1);

        processor.process(payload, "order.created");

        verify(stockService).commit(reservationId, 91L);
        verify(stockService, never()).release(any(), any());
        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        verify(receipts).insertIfAbsentPostgres(eq(eventId), eq("order.created"), eq("COMMIT"), eq(91L),
                eq(reservationId), fingerprint.capture());
        org.assertj.core.api.Assertions.assertThat(fingerprint.getValue()).hasSize(64);
    }

    @Test
    void retryTopicUsesTheCanonicalSourceForAnExactReplay() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String payload = payload(eventId, 91L, reservationId);
        when(receipts.insertIfAbsentPostgres(any(), any(), any(), anyLong(), any(), anyString())).thenReturn(0);
        when(receipts.findById(eventId)).thenReturn(Optional.of(receipt(
                eventId, "order.created", "COMMIT", 91L, reservationId, payload)));

        assertDoesNotThrow(() -> processor.process(payload, "order.created-retry-flashsale-1000"));

        verifyNoInteractions(stockService);
    }

    @Test
    void contradictoryReuseAfterAnotherReplicaClaimedTheReceiptFailsClosed() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String original = payload(eventId, 91L, reservationId);
        String contradictory = payload(eventId, 92L, reservationId);
        when(receipts.insertIfAbsentPostgres(any(), any(), any(), anyLong(), any(), anyString())).thenReturn(0);
        when(receipts.findById(eventId)).thenReturn(Optional.of(receipt(
                eventId, "order.created", "COMMIT", 91L, reservationId, original)));

        assertThrows(IllegalArgumentException.class, () -> processor.process(contradictory, "order.created"));

        verifyNoInteractions(stockService);
    }

    @Test
    void absentReservationStillGetsAReceiptAndDoesNotTouchStockState() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"orderId\":91}";
        when(receipts.insertIfAbsentPostgres(eq(eventId), eq("order.cancelled"), eq("RELEASE"), eq(91L),
                isNull(), anyString())).thenReturn(1);

        processor.process(payload, "order.cancelled");

        verifyNoInteractions(stockService);
    }

    private FlashSaleOrderReservationReceipt receipt(UUID eventId, String sourceTopic, String action,
                                                      long orderId, UUID reservationId, String payload)
            throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return FlashSaleOrderReservationReceipt.builder()
                .eventId(eventId).sourceTopic(sourceTopic).action(action).orderId(orderId)
                .reservationId(reservationId)
                .payloadFingerprint(java.util.HexFormat.of().formatHex(
                        digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    private String payload(UUID eventId, long orderId, UUID reservationId) {
        return "{\"eventId\":\"" + eventId + "\",\"orderId\":" + orderId
                + ",\"flashSaleReservationId\":\"" + reservationId + "\"}";
    }
}
