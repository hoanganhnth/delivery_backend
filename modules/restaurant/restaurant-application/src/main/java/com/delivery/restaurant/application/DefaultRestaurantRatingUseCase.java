package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.rating.RestaurantRatingConflictException;
import com.delivery.restaurant.domain.rating.RestaurantRatingStatus;
import java.util.List;

/** Rating eligibility, mutation ordering, moderation and approved-only public reads. */
public final class DefaultRestaurantRatingUseCase implements RestaurantRatingUseCase {
    public static final String DUPLICATE_MESSAGE = "Order has already been rated for this restaurant";
    private final RestaurantRatingStorePort ratings;
    private final RatingOrderEligibilityPort orders;
    private final RestaurantTransactionPort transactions;

    public DefaultRestaurantRatingUseCase(RestaurantRatingStorePort ratings,
            RatingOrderEligibilityPort orders, RestaurantTransactionPort transactions) {
        this.ratings = ratings;
        this.orders = orders;
        this.transactions = transactions;
    }

    @Override public RestaurantRatingResult submitRating(SubmitRestaurantRatingCommand command) {
        return transactions.required(() -> {
            orders.requireDeliveredOrder(command.orderId(), command.customerId(), command.restaurantId());
            ratings.lockRestaurant(command.restaurantId());
            if (!ratings.restaurantExists(command.restaurantId()))
                throw new IllegalArgumentException("Restaurant not found with ID: " + command.restaurantId());
            if (ratings.orderRated(command.orderId())) throw new RestaurantRatingConflictException(DUPLICATE_MESSAGE);
            var result = ratings.insert(command);
            updateAggregate(command.restaurantId());
            return result;
        });
    }

    @Override public RestaurantRatingResult updateRatingStatus(Long ratingId, String status) {
        return transactions.required(() -> {
            var current = ratings.find(ratingId)
                    .orElseThrow(() -> new IllegalArgumentException("Rating not found with ID: " + ratingId));
            ratings.lockRestaurant(current.restaurantId());
            var result = ratings.updateStatus(ratingId, RestaurantRatingStatus.valueOf(status.toUpperCase()));
            if (!ratings.restaurantExists(current.restaurantId())) throw new IllegalArgumentException("Restaurant not found");
            updateAggregate(current.restaurantId());
            return result;
        });
    }

    private void updateAggregate(Long restaurantId) {
        var aggregate = ratings.aggregate(restaurantId, RestaurantRatingStatus.APPROVED);
        ratings.updateRestaurantAggregate(restaurantId, aggregate.average(), Math.toIntExact(aggregate.count()));
    }

    @Override public List<RestaurantRatingResult> getRestaurantRatings(Long id) {
        return ratings.byRestaurant(id, RestaurantRatingStatus.APPROVED, 100);
    }
    @Override public List<RestaurantRatingResult> getMyRatings(Long id) { return ratings.byCustomer(id, 100); }
    @Override public List<RestaurantRatingResult> getAllRatings() { return ratings.all(100); }
    @Override public RestaurantRatingPage getRestaurantRatingsPage(Long id, int page, int size) {
        return ratings.byRestaurantPage(id, RestaurantRatingStatus.APPROVED, page, size);
    }
    @Override public RestaurantRatingPage getAllRatingsPage(int page, int size) { return ratings.allPage(page, size); }
}
