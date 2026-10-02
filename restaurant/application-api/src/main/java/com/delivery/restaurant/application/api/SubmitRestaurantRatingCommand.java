package com.delivery.restaurant.application.api;

/** Trusted rating command; customer identity comes from the authenticated actor. */
public record SubmitRestaurantRatingCommand(
        Long restaurantId,
        Long customerId,
        Long orderId,
        Integer rating,
        String comment) {
}
