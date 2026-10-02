package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.decision.RestaurantDecisionKind;

public record RestaurantDecisionCommand(Long orderId, Long restaurantId, Long actorUserId,
        RestaurantDecisionKind decision, Integer estimatedPrepTime, String notes, String rejectionReason) {}
