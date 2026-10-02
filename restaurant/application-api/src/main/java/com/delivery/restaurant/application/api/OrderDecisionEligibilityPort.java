package com.delivery.restaurant.application.api;

public interface OrderDecisionEligibilityPort {
    void requirePendingOrderForRestaurant(Long orderId, Long restaurantId);
}
