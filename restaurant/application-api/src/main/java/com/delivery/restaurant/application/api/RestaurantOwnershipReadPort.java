package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import java.util.Optional;

public interface RestaurantOwnershipReadPort {
    Optional<RestaurantManagementFacts> findOwnership(Long restaurantId);
}
