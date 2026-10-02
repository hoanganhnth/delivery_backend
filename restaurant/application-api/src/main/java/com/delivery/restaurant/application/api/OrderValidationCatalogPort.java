package com.delivery.restaurant.application.api;

import java.util.List;
import java.util.Optional;

/** Canonical unfiltered catalog facts from the current validation snapshot. */
public interface OrderValidationCatalogPort {
    Optional<RestaurantSnapshot> findRestaurant(Long id);
    List<MenuItemSnapshot> findItems(List<Long> ids);
}
