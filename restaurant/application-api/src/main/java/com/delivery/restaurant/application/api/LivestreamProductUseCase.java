package com.delivery.restaurant.application.api;

import java.util.Optional;

public interface LivestreamProductUseCase {
    Optional<LivestreamProductSnapshot> findAvailable(Long restaurantId, Long productId);
}
