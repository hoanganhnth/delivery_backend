package com.delivery.restaurant.application.api;

import java.util.Optional;

public interface UpdateRestaurantUseCase {
    Optional<RestaurantUpdateResult> update(UpdateRestaurantCommand command);
}
