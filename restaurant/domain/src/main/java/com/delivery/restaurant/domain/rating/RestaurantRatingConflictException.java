package com.delivery.restaurant.domain.rating;

public class RestaurantRatingConflictException extends RuntimeException {
    public RestaurantRatingConflictException(String message) {
        super(message);
    }
}
