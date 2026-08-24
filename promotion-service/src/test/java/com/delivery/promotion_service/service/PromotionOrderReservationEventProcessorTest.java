package com.delivery.promotion_service.service;

import com.delivery.promotion_service.dto.PromotionReservationResponse;
import com.delivery.promotion_service.dto.VoucherReservationResponse;
import com.delivery.promotion_service.entity.PromotionReservation;
import com.delivery.promotion_service.entity.PromotionOrderReservationReceipt;
import com.delivery.promotion_service.entity.VoucherReservation;
import com.delivery.promotion_service.exception.PromotionConflictException;
import com.delivery.promotion_service.repository.PromotionOrderReservationReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromotionOrderReservationEventProcessorTest {

    private final PromotionService promotionService = mock(PromotionService.class);
    private final PromotionOrderReservationReceiptRepository receipts =
            mock(PromotionOrderReservationReceiptRepository.class);
    private final PromotionOrderReservationEventProcessor processor =
            new PromotionOrderReservationEventProcessor(
                    promotionService, receipts, new ObjectMapper(), "jdbc:postgresql://db/promotion",
                    "order.created", "order.cancelled", "order.refund-eligible");

    @Test
    void firstCreatedEventClaimsReceiptThenCommitsTheBoundReservation() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String payload = payload(eventId, 91L, reservationId);
        when(receipts.insertIfAbsentPostgres(eq(eventId), eq("order.created"), eq("COMMIT"), eq(91L),
                eq(reservationId), anyString())).thenReturn(1);
        when(promotionService.commitReservation(reservationId, 91L)).thenReturn(
                VoucherReservationResponse.builder().state(VoucherReservation.State.COMMITTED).build());

        processor.process(payload, "order.created");

        verify(promotionService).commitReservation(reservationId, 91L);
        verify(promotionService, never()).releaseReservation(any(), any());
        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        verify(receipts).insertIfAbsentPostgres(eq(eventId), eq("order.created"), eq("COMMIT"), eq(91L),
                eq(reservationId), fingerprint.capture());
        org.assertj.core.api.Assertions.assertThat(fingerprint.getValue()).hasSize(64);
    }

    @Test
    void createdEventFailsWhenLegacyCommitDoesNotReachCommittedState() {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String payload = payload(eventId, 91L, reservationId);
        when(receipts.insertIfAbsentPostgres(eq(eventId), eq("order.created"), eq("COMMIT"), eq(91L),
                eq(reservationId), anyString())).thenReturn(1);
        when(promotionService.commitReservation(reservationId, 91L)).thenReturn(
                VoucherReservationResponse.builder().state(VoucherReservation.State.EXPIRED).build());

        assertThrows(PromotionConflictException.class, () -> processor.process(payload, "order.created"));

        verify(promotionService).commitReservation(reservationId, 91L);
    }

    @Test
    void createdEventRequiresCommittedStateForStackingReservation() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID promotionReservationId = UUID.randomUUID();
        String payload = promotionPayload(eventId, 91L, promotionReservationId);
        when(receipts.insertIfAbsentPostgres(eq(eventId), eq("order.created"), eq("COMMIT"), eq(91L),
                isNull(), anyString())).thenReturn(1);
        when(promotionService.commitPromotionReservation(promotionReservationId, 91L)).thenReturn(
                PromotionReservationResponse.builder().state(PromotionReservation.State.COMMITTED).build());

        processor.process(payload, "order.created");

        verify(promotionService).commitPromotionReservation(promotionReservationId, 91L);
        verify(promotionService, never()).commitReservation(any(), any());
    }

    @Test
    void exactReplayAfterAnotherReplicaClaimedTheReceiptIsANoOp() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String payload = payload(eventId, 91L, reservationId);
        when(receipts.insertIfAbsentPostgres(any(), any(), any(), anyLong(), any(), anyString())).thenReturn(0);
        when(receipts.findById(eventId)).thenAnswer(invocation -> Optional.of(receipt(
                eventId, "order.created", "COMMIT", 91L, reservationId, payload)));

        assertDoesNotThrow(() -> processor.process(payload, "order.created"));

        verifyNoInteractions(promotionService);
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

        verifyNoInteractions(promotionService);
    }

    @Test
    void absentReservationStillGetsAReceiptAndDoesNotTouchVoucherState() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"orderId\":91}";
        when(receipts.insertIfAbsentPostgres(eq(eventId), eq("order.cancelled"), eq("RELEASE"), eq(91L),
                isNull(), anyString())).thenReturn(1);

        processor.process(payload, "order.cancelled");

        verifyNoInteractions(promotionService);
    }

    private PromotionOrderReservationReceipt receipt(UUID eventId, String sourceTopic, String action,
                                                      long orderId, UUID reservationId, String payload)
            throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return PromotionOrderReservationReceipt.builder()
                .eventId(eventId).sourceTopic(sourceTopic).action(action).orderId(orderId)
                .reservationId(reservationId)
                .payloadFingerprint(java.util.HexFormat.of().formatHex(
                        digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    private String payload(UUID eventId, long orderId, UUID reservationId) {
        return "{\"eventId\":\"" + eventId + "\",\"orderId\":" + orderId
                + ",\"voucherReservationId\":\"" + reservationId + "\"}";
    }

    private String promotionPayload(UUID eventId, long orderId, UUID reservationId) {
        return "{\"eventId\":\"" + eventId + "\",\"orderId\":" + orderId
                + ",\"promotionReservationId\":\"" + reservationId + "\"}";
    }
}
