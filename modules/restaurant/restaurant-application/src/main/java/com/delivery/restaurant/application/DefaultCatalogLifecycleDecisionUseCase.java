package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.CatalogLifecycleDecisionUseCase;
import com.delivery.restaurant.domain.catalog.CatalogActorRole;
import com.delivery.restaurant.domain.catalog.MenuItemLifecyclePolicy;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.catalog.RestaurantLifecyclePolicy;
import com.delivery.restaurant.domain.catalog.RestaurantStatus;
import java.util.Objects;

/** Coordinates catalog lifecycle policy without depending on Spring or persistence. */
public final class DefaultCatalogLifecycleDecisionUseCase
        implements CatalogLifecycleDecisionUseCase {

    private final RestaurantLifecyclePolicy restaurantPolicy;
    private final MenuItemLifecyclePolicy menuItemPolicy;

    public DefaultCatalogLifecycleDecisionUseCase(
            RestaurantLifecyclePolicy restaurantPolicy,
            MenuItemLifecyclePolicy menuItemPolicy) {
        this.restaurantPolicy = Objects.requireNonNull(restaurantPolicy, "restaurantPolicy");
        this.menuItemPolicy = Objects.requireNonNull(menuItemPolicy, "menuItemPolicy");
    }

    @Override
    public RestaurantStatus decideRestaurant(
            RestaurantStatus current,
            RestaurantStatus target,
            CatalogActorRole actorRole) {
        return restaurantPolicy.transition(current, target, actorRole);
    }

    @Override
    public MenuItemStatus decideMenuItem(
            MenuItemStatus current,
            MenuItemStatus target,
            CatalogActorRole actorRole) {
        return menuItemPolicy.transition(current, target, actorRole);
    }
}
