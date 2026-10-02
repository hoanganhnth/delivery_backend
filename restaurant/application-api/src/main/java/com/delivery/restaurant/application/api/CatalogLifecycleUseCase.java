package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;

public interface CatalogLifecycleUseCase {
    RestaurantSnapshot changeRestaurant(Long id, RestaurantStatus target, Long expectedVersion,
            Long principalId, Long legacyUserId, String role);
    MenuItemSnapshot changeMenuItem(Long id, MenuItemStatus target, Long expectedVersion,
            Long principalId, Long legacyUserId, String role);
}
