package com.delivery.restaurant_service.exception;

public class RestaurantRatingConflictException extends RuntimeException {
    public RestaurantRatingConflictException(String message) {
        super(message);
    }
}
