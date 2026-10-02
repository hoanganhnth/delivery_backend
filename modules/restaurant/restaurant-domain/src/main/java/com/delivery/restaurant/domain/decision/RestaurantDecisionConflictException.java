package com.delivery.restaurant.domain.decision;

public class RestaurantDecisionConflictException extends RuntimeException {
    public RestaurantDecisionConflictException(String message) { super(message); }
}
