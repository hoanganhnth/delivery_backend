package com.delivery.livestream.api;
import java.math.BigDecimal;
public record ProductSnapshot(Long id, Long productId, Long restaurantId, BigDecimal price, Boolean pinned) { }
