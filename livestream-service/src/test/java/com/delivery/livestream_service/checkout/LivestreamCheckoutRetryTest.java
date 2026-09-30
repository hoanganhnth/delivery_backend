package com.delivery.livestream_service.checkout;

import com.delivery.livestream_service.dto.request.LivestreamCheckoutQuoteRequest;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import com.delivery.livestream_service.service.LivestreamCheckoutQuoteService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LivestreamCheckoutRetryTest {
    @Test
    void retryWithSameIdempotencyKeyReturnsOriginalContext() {
        var rooms = mock(LivestreamRepository.class); var products = mock(LivestreamProductRepository.class);
        var service = new LivestreamCheckoutQuoteService(rooms, products);
        UUID streamId = UUID.randomUUID(); var room = new Livestream(); room.setId(streamId);
        room.setRestaurantId(42L); room.setSellerId(7L); room.setStatus(LivestreamStatus.LIVE);
        var product = new LivestreamProduct(); product.setId(900L); product.setLivestreamId(streamId);
        product.setProductId(10L); product.setRestaurantId(42L); product.setIsPinned(true);
        product.setPriceAtLive(new BigDecimal("99000"));
        when(rooms.findById(streamId)).thenReturn(Optional.of(room));
        when(products.findByLivestreamIdAndIsPinnedTrueAndProductIdIn(any(), any())).thenReturn(List.of(product));
        var request = new LivestreamCheckoutQuoteRequest(); request.setLivestreamId(streamId);
        request.setRestaurantId(42L); request.setProductIds(List.of(10L));

        var first = service.orderContext(request, 123L, "corr-1", "idem-1");
        var second = service.orderContext(request, 123L, "corr-2", "idem-1");

        assertThat(second).isEqualTo(first);
        verify(rooms, times(1)).findById(streamId);
    }
}
