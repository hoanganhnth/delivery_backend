package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantActorRole;

/** Framework-free inventory management mutation. */
public record UpdateMenuItemInventoryCommand(
        Integer onHandQuantity,
        Long expectedRevision,
        Long actorId,
        RestaurantActorRole actorRole) {
}
