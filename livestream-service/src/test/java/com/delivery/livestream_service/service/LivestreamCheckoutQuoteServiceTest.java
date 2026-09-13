package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.request.LivestreamCheckoutQuoteRequest;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LivestreamCheckoutQuoteServiceTest {
    private final LivestreamRepository rooms = mock(LivestreamRepository.class);
    private final LivestreamProductRepository products = mock(LivestreamProductRepository.class);
    private final LivestreamCheckoutQuoteService service = new LivestreamCheckoutQuoteService(rooms, products);

    @Test
    void returnsOnlyRequestedProductsThatAreCurrentlyPinned() {
        UUID roomId = UUID.randomUUID();
        when(rooms.findById(roomId)).thenReturn(Optional.of(room(roomId, 42L, LivestreamStatus.LIVE)));
        when(products.findByLivestreamIdAndIsPinnedTrueAndProductIdIn(
                eq(roomId), eq(List.of(20L, 30L, 10L))))
                .thenReturn(List.of(product(roomId, 10L, 42L, "89000"),
                        product(roomId, 20L, 42L, "99000")));

        var quote = service.quote(request(roomId, 42L, List.of(20L, 30L, 10L)));

        assertThat(quote.livestreamId()).isEqualTo(roomId);
        assertThat(quote.restaurantId()).isEqualTo(42L);
        assertThat(quote.items()).extracting(item -> item.productId()).containsExactly(20L, 10L);
        assertThat(quote.items()).extracting(item -> item.priceAtLive())
                .containsExactly(new BigDecimal("99000"), new BigDecimal("89000"));
    }

    @Test
    void rejectsForeignRestaurantAndEndedRoom() {
        UUID roomId = UUID.randomUUID();
        when(rooms.findById(roomId)).thenReturn(Optional.of(room(roomId, 42L, LivestreamStatus.LIVE)));
        assertThatThrownBy(() -> service.quote(request(roomId, 43L, List.of(10L))))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);

        when(rooms.findById(roomId)).thenReturn(Optional.of(room(roomId, 42L, LivestreamStatus.ENDED)));
        assertThatThrownBy(() -> service.quote(request(roomId, 42L, List.of(10L))))
                .isInstanceOf(InvalidLivestreamStatusException.class);
    }

    @Test
    void failsClosedWhenRequestedPinnedPriceIsInvalid() {
        UUID roomId = UUID.randomUUID();
        when(rooms.findById(roomId)).thenReturn(Optional.of(room(roomId, 42L, LivestreamStatus.LIVE)));
        when(products.findByLivestreamIdAndIsPinnedTrueAndProductIdIn(eq(roomId), eq(List.of(10L))))
                .thenReturn(List.of(product(roomId, 10L, 42L, "0")));

        assertThatThrownBy(() -> service.quote(request(roomId, 42L, List.of(10L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("price");
    }

    private LivestreamCheckoutQuoteRequest request(UUID roomId, long restaurantId, List<Long> productIds) {
        var request = new LivestreamCheckoutQuoteRequest();
        request.setLivestreamId(roomId);
        request.setRestaurantId(restaurantId);
        request.setProductIds(productIds);
        return request;
    }

    private Livestream room(UUID id, long restaurantId, LivestreamStatus status) {
        var room = new Livestream();
        room.setId(id);
        room.setRestaurantId(restaurantId);
        room.setStatus(status);
        return room;
    }

    private LivestreamProduct product(UUID roomId, long productId, long restaurantId, String price) {
        var product = new LivestreamProduct();
        product.setLivestreamId(roomId);
        product.setProductId(productId);
        product.setRestaurantId(restaurantId);
        product.setIsPinned(true);
        product.setPriceAtLive(new BigDecimal(price));
        return product;
    }
}
