package com.delivery.restaurant.application.api;

public interface RatingOrderEligibilityPort {
    void requireDeliveredOrder(Long orderId, Long customerId, Long restaurantId);
}
