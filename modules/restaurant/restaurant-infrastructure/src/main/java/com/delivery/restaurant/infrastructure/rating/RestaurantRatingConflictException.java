package com.delivery.restaurant.infrastructure.rating;

/** Conflict raised when an order has already produced a Restaurant rating. */
public class RestaurantRatingConflictException extends RuntimeException {

    public RestaurantRatingConflictException(String message) {
        super(message);
    }
}
