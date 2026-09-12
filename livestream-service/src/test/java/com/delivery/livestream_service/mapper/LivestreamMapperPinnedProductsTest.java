package com.delivery.livestream_service.mapper;

import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LivestreamMapperPinnedProductsTest {
    @Test
    void responseContainsOnlyPinnedProducts() {
        Livestream livestream = new Livestream();
        LivestreamProduct pinned = new LivestreamProduct();
        pinned.setProductId(1L);
        pinned.setIsPinned(true);
        LivestreamProduct unpinned = new LivestreamProduct();
        unpinned.setProductId(2L);
        unpinned.setIsPinned(false);
        livestream.setProducts(List.of(pinned, unpinned));

        var response = new LivestreamMapper().toResponse(livestream);

        assertThat(response.getPinnedProducts()).extracting("productId").containsExactly(1L);
    }
}
