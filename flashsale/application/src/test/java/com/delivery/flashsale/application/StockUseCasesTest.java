package com.delivery.flashsale.application;

import com.delivery.flashsale.application.api.StockPort;
import com.delivery.flashsale.application.api.StockPort.Item;
import com.delivery.flashsale.application.api.StockPort.Line;
import com.delivery.flashsale.application.api.StockPort.Reservation;
import com.delivery.flashsale.domain.FlashSaleInputs;
import com.delivery.flashsale.domain.FlashSaleReservationPolicy.Identity;
import com.delivery.flashsale.domain.FlashSaleReservationPolicy.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockUseCasesTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
    private final UUID reservationId = UUID.randomUUID();
    private final StockPort<String, String> port = mock(StockPort.class);
    private final StockUseCases<String, String> useCases = new StockUseCases<>(port);

    @Test
    void quoteSortsSnapshotsAndNeverLocksOrWrites() {
        var request = request(null);
        Item first = item(1L, 0, 10);
        Item second = item(2L, 0, 10);
        when(port.items(Set.of(1L, 2L), false)).thenReturn(List.of(second, first));
        when(port.time()).thenReturn(now.toLocalTime());
        List<Line> lines = List.of(new Line(1L, 11L, 1, new BigDecimal("50")),
                new Line(2L, 12L, 2, new BigDecimal("50")));
        when(port.quote(9L, lines)).thenReturn("quote");
        assertThat(useCases.quote(request)).isEqualTo("quote");
        verify(first, never()).soldQuantity(anyInt());
        verify(second, never()).soldQuantity(anyInt());
        verify(port, never()).items(any(), eq(true));
        verify(port, never()).create(any(), any(), any(), any());
        verify(port, never()).saveAndFlush(any());
        verify(port, never()).enqueue(any());
    }

    @Test
    void missingRowsAndInvalidInputsFailBeforeMutation() {
        var request = request(null);
        Item found = item(1L, 0, 10);
        when(port.items(any(), anyBoolean())).thenReturn(List.of(found));
        assertThatThrownBy(() -> useCases.quote(request)).hasMessage("Flash sale item not found");
        assertThatThrownBy(() -> useCases.reserve(request)).hasMessage("Flash sale item not found");
        assertThatThrownBy(() -> useCases.quote(null)).hasMessage("Invalid flash-sale quote request");
        assertThatThrownBy(() -> useCases.reserve(null)).hasMessage("Invalid flash sale reservation request");
        verify(port, never()).time();
        verify(port, never()).create(any(), any(), any(), any());
        verify(port, never()).saveAndFlush(any());
    }

    @Test
    void reserveValidatesEveryLineBeforeCountersAndFlushesBeforeOutbox() {
        var request = request(70L);
        Item first = item(1L, 3, 10);
        Item second = item(2L, 4, 10);
        when(port.principalEnforced()).thenReturn(true);
        when(port.items(Set.of(1L, 2L), true)).thenReturn(List.of(first, second));
        when(port.time()).thenReturn(now.toLocalTime());
        when(port.now()).thenReturn(now);
        Reservation reservation = mock(Reservation.class);
        when(port.create(request, State.RESERVED, now, now.plusMinutes(15))).thenReturn(reservation);
        when(port.response(reservation)).thenReturn("reserved");
        assertThat(useCases.reserve(request)).isEqualTo("reserved");
        var order = inOrder(first, second, reservation, port);
        order.verify(reservation).addLine(new Line(1L, 11L, 1, new BigDecimal("50")));
        order.verify(reservation).addLine(new Line(2L, 12L, 2, new BigDecimal("50")));
        order.verify(first).soldQuantity(4);
        order.verify(second).soldQuantity(6);
        order.verify(port).saveAndFlush(reservation);
        order.verify(port).enqueue(reservation);
        order.verify(port).response(reservation);
        verify(port, never()).legacyFallback();
    }

    @Test
    void unavailableSecondLineChangesNoCounterAndPersistsNothing() {
        var request = request(null);
        Item first = item(1L, 0, 10);
        Item exhausted = item(2L, 10, 10);
        when(port.items(any(), eq(true))).thenReturn(List.of(first, exhausted));
        when(port.time()).thenReturn(now.toLocalTime());
        when(port.now()).thenReturn(now);
        when(port.create(any(), any(), any(), any())).thenReturn(mock(Reservation.class));
        assertThatThrownBy(() -> useCases.reserve(request)).hasMessage("Out of stock for flash sale item 2");
        verify(first, never()).soldQuantity(anyInt());
        verify(exhausted, never()).soldQuantity(anyInt());
        verify(port, never()).saveAndFlush(any());
        verify(port, never()).enqueue(any());
        verify(port).legacyFallback();
    }

    @ParameterizedTest
    @EnumSource(State.class)
    void exactReplayFromEitherIdentityReturnsStoredTerminalOrReservedResponse(State state) {
        var request = request(null);
        Reservation reservation = reservation(state, now.plusMinutes(15));
        when(reservation.identity()).thenReturn(new Identity(reservationId, 101L, 7L, null, 9L, Map.of(1L, 1, 2L, 2)));
        when(port.response(reservation)).thenReturn(state.name());
        when(port.find(reservationId)).thenReturn(Optional.of(reservation));
        assertThat(useCases.reserve(request)).isEqualTo(state.name());
        verify(port, never()).findOrder(any());
        when(port.find(reservationId)).thenReturn(Optional.empty());
        when(port.findOrder(101L)).thenReturn(Optional.of(reservation));
        assertThat(useCases.reserve(request)).isEqualTo(state.name());
        verify(port, never()).items(any(), anyBoolean());
        verify(port, never()).enqueue(any());
        when(request.getUserId()).thenReturn(8L);
        assertThatThrownBy(() -> useCases.reserve(request)).hasMessage("Reservation replay payload does not match");
    }

    @Test
    void missingEnforcedPrincipalFailsBeforeReplayOrFallback() {
        when(port.principalEnforced()).thenReturn(true);
        assertThatThrownBy(() -> useCases.reserve(request(null)))
                .hasMessage("userPrincipalId is required when principal ownership is enforced");
        verify(port, never()).find(any());
        verify(port, never()).legacyFallback();
    }

    @Test
    void nonenforcedOptionalPrincipalKeepsServiceCompatibilityWithoutLegacyFallback() {
        var request = request(-1L);
        Reservation reservation = reservation(State.RESERVED, now.plusMinutes(15));
        when(reservation.identity()).thenReturn(new Identity(reservationId, 101L, 7L, -1L, 9L, Map.of(1L, 1, 2L, 2)));
        when(port.find(reservationId)).thenReturn(Optional.of(reservation));
        when(port.response(reservation)).thenReturn("replay");
        assertThat(useCases.reserve(request)).isEqualTo("replay");
        verify(port, never()).legacyFallback();
    }

    @ParameterizedTest
    @EnumSource(State.class)
    void commitReleaseAndReplayFollowCompleteStateMatrix(State state) {
        Reservation reservation = reservation(state, now.plusNanos(1));
        when(port.lock(reservationId)).thenReturn(Optional.of(reservation));
        when(port.now()).thenReturn(now);
        when(port.response(reservation)).thenReturn("response");
        if (state == State.RESERVED || state == State.COMMITTED) {
            assertThat(useCases.commit(reservationId, 101L)).isEqualTo("response");
            verify(reservation, times(state == State.RESERVED ? 1 : 0)).state(State.COMMITTED);
        } else {
            assertThatThrownBy(() -> useCases.commit(reservationId, 101L))
                    .hasMessage("Flash sale reservation cannot be committed from state " + state);
        }
        clearInvocations(reservation, port);
        Item item = item(1L, 3, 10);
        when(port.items(List.of(1L), true)).thenReturn(List.of(item));
        assertThat(useCases.release(reservationId, 101L)).isEqualTo("response");
        boolean release = state == State.RESERVED || state == State.COMMITTED;
        verify(item, times(release ? 1 : 0)).soldQuantity(2);
        verify(reservation, times(release ? 1 : 0)).state(State.RELEASED);
        verify(port, times(release ? 1 : 0)).enqueue(reservation);
    }

    @Test
    void lockValidationAndExpiredCommitKeepStateAndCapacityUnchanged() {
        assertThatThrownBy(() -> useCases.commit(null, 101L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCases.release(reservationId, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCases.commit(reservationId, 101L)).hasMessage("Flash sale reservation not found");
        Reservation reservation = reservation(State.RESERVED, now);
        when(port.lock(reservationId)).thenReturn(Optional.of(reservation));
        assertThatThrownBy(() -> useCases.release(reservationId, 102L)).hasMessage("reservationId is bound to another order");
        when(port.now()).thenReturn(now);
        assertThatThrownBy(() -> useCases.commit(reservationId, 101L)).hasMessage("Flash sale reservation expired before commit");
        verify(reservation, never()).state(any());
        verify(port, never()).items(any(), anyBoolean());
        verify(port, never()).enqueue(any());
    }

    @Test
    void expiryRechecksLockedStateAndClockSkipsMissingAndUnexpiredRows() {
        var missingId = UUID.randomUUID();
        var futureId = UUID.randomUUID();
        var committedId = UUID.randomUUID();
        Reservation expired = reservation(State.RESERVED, now);
        Reservation future = reservation(State.RESERVED, now.plusNanos(1));
        Reservation committed = reservation(State.COMMITTED, now.minusMinutes(1));
        Item item = item(1L, 1, 10);
        when(port.now()).thenReturn(now);
        when(port.due(now)).thenReturn(List.of(missingId, futureId, committedId, reservationId));
        when(port.lock(futureId)).thenReturn(Optional.of(future));
        when(port.lock(committedId)).thenReturn(Optional.of(committed));
        when(port.lock(reservationId)).thenReturn(Optional.of(expired));
        when(port.items(List.of(1L), true)).thenReturn(List.of(item));
        assertThat(useCases.expire()).isEqualTo(1);
        verify(item).soldQuantity(0);
        verify(expired).state(State.EXPIRED);
        verify(port).enqueue(expired);
        verify(future, never()).state(any());
        verify(committed, never()).state(any());
    }

    @Test
    void midnightResetLedgerDefectAndMissingRowsRemainFailClosed() {
        Reservation reservation = reservation(State.COMMITTED, now.plusMinutes(1));
        when(port.lock(reservationId)).thenReturn(Optional.of(reservation));
        when(port.items(List.of(1L), true)).thenReturn(List.of());
        assertThatThrownBy(() -> useCases.release(reservationId, 101L))
                .hasMessage("Flash sale stock ledger is inconsistent");
        Item resetItem = item(1L, 0, 10);
        when(port.items(List.of(1L), true)).thenReturn(List.of(resetItem));
        assertThatThrownBy(() -> useCases.release(reservationId, 101L))
                .hasMessage("Flash sale stock ledger is inconsistent");
        verify(reservation, never()).state(any());
        verify(resetItem, never()).soldQuantity(anyInt());
        verify(port, never()).enqueue(any());
    }

    private FlashSaleInputs.Reservation request(Long principalId) {
        var request = mock(FlashSaleInputs.Reservation.class);
        when(request.getReservationId()).thenReturn(reservationId);
        when(request.getOrderId()).thenReturn(101L);
        when(request.getUserId()).thenReturn(7L);
        when(request.getUserPrincipalId()).thenReturn(principalId);
        when(request.getRestaurantId()).thenReturn(9L);
        doReturn(List.of(inputLine(2L, 2), inputLine(1L, 1))).when(request).getItems();
        return request;
    }

    private FlashSaleInputs.Line inputLine(Long id, int quantity) {
        var line = mock(FlashSaleInputs.Line.class);
        when(line.getFlashSaleItemId()).thenReturn(id);
        when(line.getQuantity()).thenReturn(quantity);
        return line;
    }

    private Item item(Long id, int sold, int stock) {
        Item item = mock(Item.class);
        when(item.id()).thenReturn(id);
        when(item.restaurantId()).thenReturn(9L);
        when(item.approved()).thenReturn(true);
        when(item.campaignActive()).thenReturn(true);
        when(item.campaignStartTime()).thenReturn(LocalTime.MIN);
        when(item.campaignEndTime()).thenReturn(LocalTime.MAX);
        when(item.stockQuantity()).thenReturn(stock);
        when(item.soldQuantity()).thenReturn(sold);
        when(item.menuItemId()).thenReturn(id + 10);
        when(item.price()).thenReturn(new BigDecimal("50"));
        return item;
    }

    private Reservation reservation(State state, LocalDateTime expiry) {
        Reservation reservation = mock(Reservation.class);
        when(reservation.id()).thenReturn(reservationId);
        when(reservation.orderId()).thenReturn(101L);
        when(reservation.state()).thenReturn(state);
        when(reservation.expiresAt()).thenReturn(expiry);
        when(reservation.lines()).thenReturn(List.of(new Line(1L, 11L, 1, new BigDecimal("50"))));
        return reservation;
    }
}
