package com.delivery.livestream_service.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LivestreamCheckoutQuoteResponse(
        UUID livestreamId,
        Long restaurantId,
        List<Item> items) {

    public record Item(Long productId, BigDecimal priceAtLive) {
    }
}
