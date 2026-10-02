package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.catalog.*;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import java.util.Optional;

public interface CatalogLifecycleStorePort {
    record MenuFacts(MenuItemSnapshot snapshot, RestaurantManagementFacts ownership) { }
    Optional<RestaurantSnapshot> findRestaurant(Long id);
    Optional<MenuFacts> findMenuItem(Long id);
    RestaurantSnapshot saveRestaurantStatus(Long id, RestaurantStatus status);
    MenuItemSnapshot saveMenuItemStatus(Long id, MenuItemStatus status);
}
