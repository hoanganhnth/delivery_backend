package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.entity.FlashSaleReservation;
import com.delivery.flashsale_service.entity.FlashSaleReservationLine;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import com.delivery.flashsale_service.repository.FlashSaleReservationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FlashSaleReservationReplayTest {
    @ParameterizedTest
    @EnumSource(FlashSaleReservation.State.class)
    void exactRetryPreservesEveryStoredStateAndPriceWithoutReservingAgain(FlashSaleReservation.State state) {
        Fixture fixture = fixture(state);
        when(fixture.reservations.findById(fixture.stored.getReservationId()))
                .thenReturn(Optional.of(fixture.stored));

        var response = fixture.service.reserveStock(fixture.request);

        assertThat(response.getState()).isEqualTo(state);
        assertThat(response.getReservationId()).isEqualTo(fixture.stored.getReservationId());
        assertThat(response.getOrderId()).isEqualTo(91L);
        assertThat(response.getExpiresAt()).isEqualTo(fixture.stored.getExpiresAt());
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("12500");
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(fixture.stored.getState()).isEqualTo(state);
        verify(fixture.reservations).findById(fixture.stored.getReservationId());
        verifyNoMoreInteractions(fixture.reservations);
        verifyNoInteractions(fixture.items, fixture.outbox);
    }

    static Stream<Consumer<FlashSaleReservationRequest>> changedPayloads() {
        return Stream.of(
                request -> request.setOrderId(92L),
                request -> request.setUserId(8L),
                request -> request.setUserPrincipalId(71L),
                request -> request.setUserPrincipalId(null),
                request -> request.setRestaurantId(10L),
                request -> request.setItems(List.of(line(41L, 3), line(42L, 1))),
                request -> request.setItems(List.of(line(41L, 2))),
                request -> request.setItems(List.of(line(43L, 2), line(42L, 1))));
    }

    @ParameterizedTest
    @MethodSource("changedPayloads")
    void changedReplayFailsBeforeStockOrOutboxWrites(Consumer<FlashSaleReservationRequest> change) {
        Fixture fixture = fixture(FlashSaleReservation.State.RESERVED);
        when(fixture.reservations.findById(fixture.stored.getReservationId()))
                .thenReturn(Optional.of(fixture.stored));
        change.accept(fixture.request);

        assertThatThrownBy(() -> fixture.service.reserveStock(fixture.request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reservation replay payload does not match");

        assertThat(fixture.stored.getState()).isEqualTo(FlashSaleReservation.State.RESERVED);
        assertThat(fixture.stored.getLines().get(0).getQuantity()).isEqualTo(2);
        verify(fixture.reservations).findById(fixture.stored.getReservationId());
        verifyNoMoreInteractions(fixture.reservations);
        verifyNoInteractions(fixture.items, fixture.outbox);
    }

    @Test
    void sameOrderWithDifferentReservationKeyCannotReserveAgain() {
        Fixture fixture = fixture(FlashSaleReservation.State.COMMITTED);
        fixture.request.setReservationId(UUID.randomUUID());
        when(fixture.reservations.findByOrderId(91L)).thenReturn(Optional.of(fixture.stored));

        assertThatThrownBy(() -> fixture.service.reserveStock(fixture.request))
                .hasMessage("Reservation replay payload does not match");

        assertThat(fixture.stored.getState()).isEqualTo(FlashSaleReservation.State.COMMITTED);
        verify(fixture.reservations).findById(fixture.request.getReservationId());
        verify(fixture.reservations).findByOrderId(91L);
        verifyNoMoreInteractions(fixture.reservations);
        verifyNoInteractions(fixture.items, fixture.outbox);
    }

    private Fixture fixture(FlashSaleReservation.State state) {
        var items = mock(FlashSaleItemRepository.class);
        var reservations = mock(FlashSaleReservationRepository.class);
        var outbox = mock(FlashSaleOutboxService.class);
        var service = new FlashSaleStockService(items, reservations, outbox, new SimpleMeterRegistry());
        var stored = FlashSaleReservation.builder().reservationId(UUID.randomUUID()).orderId(91L)
                .userId(7L).userPrincipalId(70L).restaurantId(9L).state(state)
                .expiresAt(LocalDateTime.of(2026, 1, 1, 12, 0)).build();
        stored.getLines().add(FlashSaleReservationLine.builder().reservation(stored)
                .flashSaleItemId(41L).menuItemId(501L).quantity(2).unitPrice(new BigDecimal("12500")).build());
        stored.getLines().add(FlashSaleReservationLine.builder().reservation(stored)
                .flashSaleItemId(42L).menuItemId(502L).quantity(1).unitPrice(new BigDecimal("15000")).build());
        var request = new FlashSaleReservationRequest();
        request.setReservationId(stored.getReservationId());
        request.setOrderId(91L);
        request.setUserId(7L);
        request.setUserPrincipalId(70L);
        request.setRestaurantId(9L);
        // Incoming order differs from the persisted lines: identity is a set of item/quantity pairs.
        request.setItems(List.of(line(42L, 1), line(41L, 2)));
        return new Fixture(service, items, reservations, outbox, stored, request);
    }

    private static ReserveItemRequest line(long id, int quantity) {
        var line = new ReserveItemRequest();
        line.setFlashSaleItemId(id);
        line.setQuantity(quantity);
        return line;
    }

    private record Fixture(FlashSaleStockService service, FlashSaleItemRepository items,
                           FlashSaleReservationRepository reservations, FlashSaleOutboxService outbox,
                           FlashSaleReservation stored, FlashSaleReservationRequest request) { }
}
