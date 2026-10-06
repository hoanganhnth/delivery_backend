package com.delivery.flashsale.application;

import com.delivery.flashsale.application.api.*;
import com.delivery.flashsale.domain.FlashSaleEventPolicy;
import com.delivery.flashsale.domain.FlashSaleEventPolicy.Receipt;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventAndOutboxUseCasesTest {
    private final UUID eventId = UUID.randomUUID();
    private final UUID reservationId = UUID.randomUUID();
    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Test
    void receiptClaimPrecedesCommitReleaseAndMissingReservationNoop() {
        EventPort port = mock(EventPort.class);
        when(port.claim(eq(eventId), any())).thenReturn(1);
        EventUseCase useCase = new EventUseCase(port);
        for (String action : List.of("COMMIT", "RELEASE")) {
            Receipt receipt = new Receipt("topic", action, 1L, reservationId, "raw fingerprint");
            useCase.process(eventId, receipt);
            var order = inOrder(port);
            order.verify(port).claim(eventId, receipt);
            if (action.equals("COMMIT")) order.verify(port).commit(reservationId, 1L);
            else order.verify(port).release(reservationId, 1L);
        }
        useCase.process(eventId, new Receipt("topic", "COMMIT", 1L, null, "raw fingerprint"));
        verify(port, times(1)).commit(reservationId, 1L);
        verify(port, times(1)).release(reservationId, 1L);
        verify(port, never()).find(any());
    }

    @Test
    void exactReceiptReplayDoesNotMutateAndContradictionOrMissingReceiptFailsClosed() {
        EventPort port = mock(EventPort.class);
        Receipt receipt = new Receipt("topic", "COMMIT", 1L, reservationId, "raw fingerprint");
        when(port.find(eventId)).thenReturn(Optional.of(receipt));
        EventUseCase useCase = new EventUseCase(port);
        useCase.process(eventId, receipt);
        assertThatThrownBy(() -> useCase.process(eventId,
                new Receipt("topic", "COMMIT", 1L, reservationId, "changed raw whitespace")))
                .hasMessage("eventId replay has a contradictory flash-sale reservation payload");
        when(port.find(eventId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.process(eventId, receipt))
                .hasMessage("flash-sale receipt conflict resolved without a committed receipt");
        verify(port, never()).commit(any(), any());
        verify(port, never()).release(any(), any());
    }

    @Test
    void outboxIdentityAndReplayAreDeterministicForEveryState() {
        OutboxPort<String> port = mock(OutboxPort.class);
        when(port.now()).thenReturn(now);
        OutboxUseCase<String> useCase = new OutboxUseCase<>(port);
        for (String state : List.of("RESERVED", "COMMITTED", "RELEASED", "EXPIRED")) {
            when(port.state(state)).thenReturn(state);
            when(port.reservationId(state)).thenReturn(reservationId);
            UUID expected = FlashSaleEventPolicy.eventId(reservationId, "FLASH_SALE_RESERVATION_" + state);
            assertThat(useCase.enqueue(state)).isEqualTo(expected);
            verify(port).save(state, expected, "FLASH_SALE_RESERVATION_" + state, now);
            when(port.exists(expected)).thenReturn(true);
            assertThat(useCase.enqueue(state)).isEqualTo(expected);
            verify(port, times(1)).save(state, expected, "FLASH_SALE_RESERVATION_" + state, now);
        }
        verify(port, times(4)).now();
    }

    @Test
    void relayProcessesBoundedBatchAndContinuesAfterRetryAndDeadFailures() throws Exception {
        RelayPort<String> port = mock(RelayPort.class);
        when(port.now()).thenReturn(now);
        when(port.due(now, 100)).thenReturn(List.of("sent", "retry", "capped", "dead", "after"));
        var retry = new IllegalStateException("temporary");
        var capped = new IllegalStateException("x".repeat(2100));
        var dead = new IllegalStateException();
        doThrow(retry).when(port).publish("retry");
        doThrow(capped).when(port).publish("capped");
        doThrow(dead).when(port).publish("dead");
        when(port.attempts("capped")).thenReturn(10);
        when(port.attempts("dead")).thenReturn(11);
        new RelayUseCase<>(port).relay();
        verify(port).sent("sent", now);
        verify(port).sent("after", now);
        verify(port).failed("retry", 1, "temporary");
        verify(port).retryAt("retry", now.plusSeconds(2));
        verify(port).failed("capped", 11, "x".repeat(2000));
        verify(port).retryAt("capped", now.plusSeconds(256));
        verify(port).failed("dead", 12, "Kafka publish failed");
        verify(port).dead("dead", dead);
        verify(port, never()).retryAt(eq("dead"), any());
    }

    @Test
    void relayEmptyBatchAndRecurringResetKeepDatabaseAuthority() {
        RelayPort<String> relay = mock(RelayPort.class);
        when(relay.now()).thenReturn(now);
        when(relay.due(now, 100)).thenReturn(List.of());
        new RelayUseCase<>(relay).relay();
        verify(relay).now();
        verify(relay).due(now, 100);
        verifyNoMoreInteractions(relay);
        RecurringStockPort recurring = mock(RecurringStockPort.class);
        when(recurring.resetApprovedRecurringStock()).thenReturn(250);
        assertThat(new RecurringStockUseCase(recurring).reset()).isEqualTo(250);
        verify(recurring).resetApprovedRecurringStock();
    }
}
