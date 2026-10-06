package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.entity.FlashSaleItem;
import com.delivery.flashsale_service.entity.FlashSaleReservation;
import com.delivery.flashsale_service.entity.FlashSaleReservationLine;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import com.delivery.flashsale_service.repository.FlashSaleReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FlashSaleStockServiceStateTransitionTest {
    @Test
    void commitTransitionsAnActiveReservationOnceAndKeepsSubsequentCommitsIdempotent() {
        Fixtures fixtures = fixtures(FlashSaleReservation.State.RESERVED, LocalDateTime.now().plusMinutes(5));
        when(fixtures.reservations.findByIdForUpdate(fixtures.reservation.getReservationId()))
                .thenReturn(Optional.of(fixtures.reservation));

        assertThat(fixtures.service.commit(fixtures.reservation.getReservationId(), fixtures.reservation.getOrderId()).getState())
                .isEqualTo(FlashSaleReservation.State.COMMITTED);
        assertThat(fixtures.service.commit(fixtures.reservation.getReservationId(), fixtures.reservation.getOrderId()).getState())
                .isEqualTo(FlashSaleReservation.State.COMMITTED);

        verify(fixtures.outbox, times(1)).enqueue(fixtures.reservation);
        verifyNoInteractions(fixtures.items);
    }

    @Test
    void lateCommitFailsWithoutAcknowledgingOrRestoringStockInline() {
        Fixtures fixtures = fixtures(FlashSaleReservation.State.RESERVED, LocalDateTime.now().minusSeconds(1));
        when(fixtures.reservations.findByIdForUpdate(fixtures.reservation.getReservationId()))
                .thenReturn(Optional.of(fixtures.reservation));

        assertThatThrownBy(() -> fixtures.service.commit(fixtures.reservation.getReservationId(),
                fixtures.reservation.getOrderId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired before commit");

        assertThat(fixtures.reservation.getState()).isEqualTo(FlashSaleReservation.State.RESERVED);
        assertThat(fixtures.item.getSoldQuantity()).isEqualTo(2);
        verifyNoInteractions(fixtures.items, fixtures.outbox);
    }

    @Test
    void commitCannotAcknowledgeAnAlreadyExpiredReservation() {
        Fixtures fixtures = fixtures(FlashSaleReservation.State.EXPIRED, LocalDateTime.now().minusMinutes(1));
        when(fixtures.reservations.findByIdForUpdate(fixtures.reservation.getReservationId()))
                .thenReturn(Optional.of(fixtures.reservation));

        assertThatThrownBy(() -> fixtures.service.commit(fixtures.reservation.getReservationId(),
                fixtures.reservation.getOrderId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be committed from state EXPIRED");
        verifyNoInteractions(fixtures.items, fixtures.outbox);
    }

    @Test
    void releaseTransitionsCommittedReservationAndReturnsItsStock() {
        Fixtures fixtures = fixtures(FlashSaleReservation.State.COMMITTED, LocalDateTime.now().plusMinutes(5));
        when(fixtures.reservations.findByIdForUpdate(fixtures.reservation.getReservationId()))
                .thenReturn(Optional.of(fixtures.reservation));
        when(fixtures.items.findAllByIdForUpdate(List.of(41L))).thenReturn(List.of(fixtures.item));

        assertThat(fixtures.service.release(fixtures.reservation.getReservationId(), fixtures.reservation.getOrderId()).getState())
                .isEqualTo(FlashSaleReservation.State.RELEASED);

        assertThat(fixtures.item.getSoldQuantity()).isZero();
        verify(fixtures.outbox).enqueue(fixtures.reservation);
    }

    @Test
    void expirySweepRechecksLockedCandidatesAndCountsOnlyTransitionedReservations() {
        Fixtures fixtures = fixtures(FlashSaleReservation.State.RESERVED, LocalDateTime.now().minusSeconds(1));
        when(fixtures.reservations.findTop100ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                eq(FlashSaleReservation.State.RESERVED), any())).thenReturn(List.of(fixtures.reservation));
        when(fixtures.reservations.findByIdForUpdate(fixtures.reservation.getReservationId()))
                .thenReturn(Optional.of(fixtures.reservation));
        when(fixtures.items.findAllByIdForUpdate(List.of(41L))).thenReturn(List.of(fixtures.item));

        assertThat(fixtures.service.expireReservations()).isEqualTo(1);
        assertThat(fixtures.reservation.getState()).isEqualTo(FlashSaleReservation.State.EXPIRED);
        assertThat(fixtures.item.getSoldQuantity()).isZero();
        verify(fixtures.outbox).enqueue(fixtures.reservation);
    }

    private Fixtures fixtures(FlashSaleReservation.State state, LocalDateTime expiresAt) {
        FlashSaleItemRepository items = mock(FlashSaleItemRepository.class);
        FlashSaleReservationRepository reservations = mock(FlashSaleReservationRepository.class);
        FlashSaleOutboxService outbox = mock(FlashSaleOutboxService.class);
        FlashSaleStockService service = new FlashSaleStockService(items, reservations, outbox, mock(MeterRegistry.class));
        FlashSaleItem item = FlashSaleItem.builder().id(41L).soldQuantity(2).stockQuantity(10).build();
        FlashSaleReservation reservation = FlashSaleReservation.builder().reservationId(UUID.randomUUID()).orderId(91L)
                .userId(7L).restaurantId(9L).state(state).expiresAt(expiresAt).build();
        reservation.getLines().add(FlashSaleReservationLine.builder().reservation(reservation).flashSaleItemId(41L)
                .menuItemId(501L).quantity(2).unitPrice(BigDecimal.TEN).build());
        return new Fixtures(service, items, reservations, outbox, item, reservation);
    }

    private record Fixtures(FlashSaleStockService service, FlashSaleItemRepository items,
                            FlashSaleReservationRepository reservations, FlashSaleOutboxService outbox,
                            FlashSaleItem item, FlashSaleReservation reservation) { }
}
