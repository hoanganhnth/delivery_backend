package com.delivery.flashsale_service.service;

import com.delivery.flashsale_service.dto.FlashSaleQuoteRequest;
import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.ReserveItemRequest;
import com.delivery.flashsale_service.entity.FlashSaleCampaign;
import com.delivery.flashsale_service.entity.FlashSaleItem;
import com.delivery.flashsale_service.repository.FlashSaleItemRepository;
import com.delivery.flashsale_service.repository.FlashSaleReservationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlashSaleStockServiceAvailabilityTest {
    @Test
    void quoteReturnsApprovedServerPricesInItemIdOrder() {
        FlashSaleItemRepository items = mock(FlashSaleItemRepository.class);
        FlashSaleStockService service = service(items, mock(FlashSaleReservationRepository.class),
                mock(FlashSaleOutboxService.class));
        FlashSaleItem first = item(41L, 10000);
        FlashSaleItem second = item(42L, 20000);
        when(items.findAllById(any())).thenReturn(List.of(second, first));
        FlashSaleQuoteRequest request = new FlashSaleQuoteRequest();
        request.setRestaurantId(9L);
        request.setItems(List.of(line(42L, 2), line(41L, 1)));

        var quote = service.quote(request);

        assertThat(quote.getRestaurantId()).isEqualTo(9L);
        assertThat(quote.getItems()).extracting(com.delivery.flashsale_service.dto.FlashSaleQuoteResponse.Line::getFlashSaleItemId)
                .containsExactly(41L, 42L);
        assertThat(quote.getItems().get(0).getUnitPrice()).isEqualByComparingTo("10000");
        assertThat(quote.getItems().get(1).getQuantity()).isEqualTo(2);
        assertThat(quote.getItems().get(1).getUnitPrice()).isEqualByComparingTo("20000");
    }

    static Stream<Arguments> unavailableItems() {
        return Stream.of(
                Arguments.of("deleted", (Consumer<FlashSaleItem>) item -> item.setDeletedAt(LocalDateTime.now()), "deleted"),
                Arguments.of("foreign restaurant", (Consumer<FlashSaleItem>) item -> item.setRestaurantId(8L), "another restaurant"),
                Arguments.of("unapproved", (Consumer<FlashSaleItem>) item -> item.setStatus(FlashSaleItem.ItemStatus.PENDING), "not approved"),
                Arguments.of("inactive campaign", (Consumer<FlashSaleItem>) item -> item.getCampaign()
                        .setStatus(FlashSaleCampaign.CampaignStatus.UPCOMING), "not active"),
                Arguments.of("outside campaign window", (Consumer<FlashSaleItem>) item -> item.getCampaign()
                        .setStartTime(LocalTime.MAX), "not active"),
                Arguments.of("sold out", (Consumer<FlashSaleItem>) item -> item.setSoldQuantity(10), "Out of stock"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailableItems")
    void quoteRejectsUnavailableItem(String caseName, Consumer<FlashSaleItem> change, String message) {
        FlashSaleItemRepository items = mock(FlashSaleItemRepository.class);
        FlashSaleStockService service = service(items, mock(FlashSaleReservationRepository.class),
                mock(FlashSaleOutboxService.class));
        FlashSaleItem item = item(41L, 10000);
        change.accept(item);
        when(items.findAllById(any())).thenReturn(List.of(item));
        FlashSaleQuoteRequest request = new FlashSaleQuoteRequest();
        request.setRestaurantId(9L);
        request.setItems(List.of(line(41L, 1)));

        assertThatThrownBy(() -> service.quote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    @Test
    void exhaustedSecondReservationLineLeavesAllStockAndPersistenceUntouched() {
        FlashSaleItemRepository items = mock(FlashSaleItemRepository.class);
        FlashSaleReservationRepository reservations = mock(FlashSaleReservationRepository.class);
        FlashSaleOutboxService outbox = mock(FlashSaleOutboxService.class);
        FlashSaleStockService service = service(items, reservations, outbox);
        FlashSaleItem available = item(41L, 10000);
        FlashSaleItem exhausted = item(42L, 20000);
        exhausted.setSoldQuantity(10);
        when(items.findAllByIdForUpdate(any())).thenReturn(List.of(available, exhausted));
        FlashSaleReservationRequest request = new FlashSaleReservationRequest();
        request.setReservationId(UUID.randomUUID());
        request.setOrderId(91L);
        request.setUserId(7L);
        request.setUserPrincipalId(70L);
        request.setRestaurantId(9L);
        request.setItems(List.of(line(41L, 1), line(42L, 1)));

        assertThatThrownBy(() -> service.reserveStock(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Out of stock");
        assertThat(available.getSoldQuantity()).isZero();
        assertThat(exhausted.getSoldQuantity()).isEqualTo(10);
        verify(reservations, never()).saveAndFlush(any());
        verifyNoInteractions(outbox);
    }

    private static FlashSaleStockService service(FlashSaleItemRepository items,
                                                 FlashSaleReservationRepository reservations,
                                                 FlashSaleOutboxService outbox) {
        return new FlashSaleStockService(items, reservations, outbox, new SimpleMeterRegistry());
    }

    private static FlashSaleItem item(long id, int price) {
        FlashSaleCampaign campaign = FlashSaleCampaign.builder()
                .status(FlashSaleCampaign.CampaignStatus.ACTIVE)
                .startTime(LocalTime.MIN).endTime(LocalTime.MAX).build();
        return FlashSaleItem.builder().id(id).campaign(campaign).restaurantId(9L)
                .menuItemId(id + 100).flashSalePrice(BigDecimal.valueOf(price))
                .stockQuantity(10).soldQuantity(0).status(FlashSaleItem.ItemStatus.APPROVED).build();
    }

    private static ReserveItemRequest line(long id, int quantity) {
        ReserveItemRequest line = new ReserveItemRequest();
        line.setFlashSaleItemId(id);
        line.setQuantity(quantity);
        return line;
    }
}
