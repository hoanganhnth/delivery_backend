package com.delivery.saga_orchestrator_service.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.delivery.saga_orchestrator_service.entity.SagaInboundReceipt;
import com.delivery.saga_orchestrator_service.repository.SagaInboundReceiptRepository;
import com.delivery.saga_orchestrator_service.repository.SagaInstanceRepository;

class SagaInboundReceiptTest {
    private static final UUID EVENT_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final String EVENT = "{\"eventId\":\"" + EVENT_ID + "\",\"orderId\":91}";

    @Test
    void exactInboundReplayDoesNotReachSagaMutationOrOutbox() throws Exception {
        SagaInstanceRepository sagas = mock(SagaInstanceRepository.class);
        SagaOutboxService outbox = mock(SagaOutboxService.class);
        SagaInboundReceiptRepository receipts = mock(SagaInboundReceiptRepository.class);
        when(receipts.findById(EVENT_ID)).thenReturn(Optional.of(new SagaInboundReceipt(
                EVENT_ID, "order.created", 91L, sha256(EVENT))));

        SagaManager manager = new SagaManager(sagas, outbox, receipts);
        assertDoesNotThrow(() -> manager.handleOrderCreated(91L, EVENT));
        verifyNoInteractions(sagas, outbox);
    }

    @Test
    void conflictingPayloadForStableEventIdIsRejectedBeforeSagaMutation() throws Exception {
        SagaInstanceRepository sagas = mock(SagaInstanceRepository.class);
        SagaOutboxService outbox = mock(SagaOutboxService.class);
        SagaInboundReceiptRepository receipts = mock(SagaInboundReceiptRepository.class);
        when(receipts.findById(EVENT_ID)).thenReturn(Optional.of(new SagaInboundReceipt(
                EVENT_ID, "order.created", 91L, sha256(EVENT))));

        SagaManager manager = new SagaManager(sagas, outbox, receipts);
        assertThrows(IllegalArgumentException.class, () -> manager.handleOrderCreated(91L,
                "{\"eventId\":\"" + EVENT_ID + "\",\"orderId\":92}"));
        verifyNoInteractions(sagas, outbox);
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) result.append(String.format("%02x", item));
        return result.toString();
    }
}
