package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;

@FunctionalInterface
public interface MenuItemCreateDecision {
    MenuItemMutationPlan decide(RestaurantManagementFacts restaurantFacts);
}
