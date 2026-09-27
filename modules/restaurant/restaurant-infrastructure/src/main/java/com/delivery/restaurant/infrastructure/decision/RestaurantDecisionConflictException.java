package com.delivery.restaurant.infrastructure.decision;

public class RestaurantDecisionConflictException extends RuntimeException {
    public RestaurantDecisionConflictException(String message) {
        super(message);
    }
}
