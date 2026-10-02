package com.delivery.restaurant.application.api;

@FunctionalInterface
public interface RestaurantUpdateDecision {
    RestaurantMutationPlan decide(RestaurantStoredFacts storedFacts);
}
