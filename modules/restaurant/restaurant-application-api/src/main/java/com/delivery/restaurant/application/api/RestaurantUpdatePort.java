package com.delivery.restaurant.application.api;

import java.util.Optional;

/** Transaction-scoped update boundary; the adapter owns persistence side effects. */
public interface RestaurantUpdatePort {
    Optional<RestaurantUpdateResult> update(
            UpdateRestaurantCommand command,
            RestaurantUpdateDecision decision);
}
