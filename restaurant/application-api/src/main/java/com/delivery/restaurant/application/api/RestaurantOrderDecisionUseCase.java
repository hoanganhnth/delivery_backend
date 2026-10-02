package com.delivery.restaurant.application.api;

public interface RestaurantOrderDecisionUseCase {
    void confirm(Long orderId, Long restaurantId, Long actorUserId, Integer estimatedPrepTime, String notes);
    void reject(Long orderId, Long restaurantId, Long actorUserId, String rejectionReason);
}
