package com.delivery.restaurant.application.api;

import java.util.Optional;

public interface LivestreamProductReadPort {
    Optional<LivestreamProductSnapshot> findProduct(Long productId);
}
