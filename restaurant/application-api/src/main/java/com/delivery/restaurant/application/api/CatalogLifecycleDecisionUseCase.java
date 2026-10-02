package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.catalog.CatalogActorRole;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;

/** Owns the application decision for a catalog lifecycle command. */
public interface CatalogLifecycleDecisionUseCase {

    RestaurantStatus decideRestaurant(
            RestaurantStatus current,
            RestaurantStatus target,
            CatalogActorRole actorRole);

    MenuItemStatus decideMenuItem(
            MenuItemStatus current,
            MenuItemStatus target,
            CatalogActorRole actorRole);
}
