package com.delivery.restaurant.application.api;

@FunctionalInterface
public interface MenuItemUpdateDecision {
    MenuItemMutationPlan decide(MenuItemStoredFacts storedFacts);
}
