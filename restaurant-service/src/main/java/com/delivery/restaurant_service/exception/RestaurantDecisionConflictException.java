package com.delivery.restaurant_service.exception;

public class RestaurantDecisionConflictException extends RuntimeException {
    public RestaurantDecisionConflictException(String message) {
        super(message);
    }
}
