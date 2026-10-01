package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.FlashSaleQuoteRequest;
import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import com.delivery.flashsale_service.repository.FlashSaleReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class FlashSaleStockServiceValidationTest {
    static Stream<List<ReserveItemRequest>> malformedLines() {
        return Stream.of(java.util.Collections.singletonList(null),
                List.of(line(null, 1)), List.of(line(0L, 1)),
                List.of(line(10L, null)), List.of(line(10L, 0)),
                List.of(line(10L, 1), line(10L, 1)));
    }

    @ParameterizedTest
    @MethodSource("malformedLines")
    void reserveRejectsMalformedLinesBeforeAnyPersistenceAccess(List<ReserveItemRequest> lines) {
        FlashSaleItemRepository items = mock(FlashSaleItemRepository.class);
        FlashSaleReservationRepository reservations = mock(FlashSaleReservationRepository.class);
        FlashSaleStockService service = new FlashSaleStockService(items, reservations,
                mock(FlashSaleOutboxService.class), mock(MeterRegistry.class));
        FlashSaleReservationRequest request = new FlashSaleReservationRequest();
        request.setReservationId(UUID.randomUUID()); request.setOrderId(91L); request.setUserId(7L);
        request.setRestaurantId(9L); request.setItems(lines);

        assertThatThrownBy(() -> service.reserveStock(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid flash sale reservation request");
        verifyNoInteractions(items, reservations);
    }

    @ParameterizedTest
    @MethodSource("malformedLines")
    void quoteRejectsMalformedLinesBeforeAnyPersistenceAccess(List<ReserveItemRequest> lines) {
        FlashSaleItemRepository items = mock(FlashSaleItemRepository.class);
        FlashSaleStockService service = new FlashSaleStockService(items,
                mock(FlashSaleReservationRepository.class), mock(FlashSaleOutboxService.class),
                mock(MeterRegistry.class));
        FlashSaleQuoteRequest request = new FlashSaleQuoteRequest();
        request.setRestaurantId(9L); request.setItems(lines);

        assertThatThrownBy(() -> service.quote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid flash-sale quote request");
        verifyNoInteractions(items);
    }

    private static ReserveItemRequest line(Long itemId, Integer quantity) {
        ReserveItemRequest line = new ReserveItemRequest();
        line.setFlashSaleItemId(itemId); line.setQuantity(quantity);
        return line;
    }
}
