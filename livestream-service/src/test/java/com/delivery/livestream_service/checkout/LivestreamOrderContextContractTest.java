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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LivestreamOrderContextContractTest {
    @Test
    void createsVersionedContextWithPinnedPriceAndCorrelation() {
        var rooms = mock(LivestreamRepository.class);
        var products = mock(LivestreamProductRepository.class);
        var service = new LivestreamCheckoutQuoteService(rooms, products);
        UUID streamId = UUID.randomUUID();
        var room = new Livestream();
        room.setId(streamId); room.setRestaurantId(42L); room.setSellerId(7L); room.setStatus(LivestreamStatus.LIVE);
        var product = new LivestreamProduct();
        product.setId(900L); product.setLivestreamId(streamId); product.setProductId(10L);
        product.setRestaurantId(42L); product.setIsPinned(true); product.setPriceAtLive(new BigDecimal("99000"));
        when(rooms.findById(streamId)).thenReturn(Optional.of(room));
        when(products.findByLivestreamIdAndIsPinnedTrueAndProductIdIn(eq(streamId), eq(List.of(10L))))
                .thenReturn(List.of(product));

        var request = new LivestreamCheckoutQuoteRequest();
        request.setLivestreamId(streamId); request.setRestaurantId(42L); request.setProductIds(List.of(10L));
        var context = service.orderContext(request, 123L, "corr-1", "idem-1").get(0);

        assertThat(context.schemaVersion()).isEqualTo(1);
        assertThat(context.streamId()).isEqualTo(streamId);
        assertThat(context.pinnedProductId()).isEqualTo(10L);
        assertThat(context.sellerId()).isEqualTo(7L);
        assertThat(context.restaurantId()).isEqualTo(42L);
        assertThat(context.priceSnapshotId()).isEqualTo(900L);
        assertThat(context.actorPrincipalId()).isEqualTo(123L);
        assertThat(context.correlationId()).isEqualTo("corr-1");
        assertThat(context.idempotencyKey()).isEqualTo("idem-1");
        assertThat(context.priceAtLive()).isEqualByComparingTo("99000");
    }
}
