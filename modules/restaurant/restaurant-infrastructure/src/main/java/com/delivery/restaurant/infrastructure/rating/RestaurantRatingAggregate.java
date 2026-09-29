package com.delivery.restaurant.infrastructure.rating;

/** Database-computed approved-rating aggregate. */
public record RestaurantRatingAggregate(long count, double average) {
}
