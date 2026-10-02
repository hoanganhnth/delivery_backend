package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.rating.RestaurantRatingStatus;
import java.util.List;
import java.util.Optional;

public interface RestaurantRatingStorePort {
    void lockRestaurant(Long restaurantId);
    boolean restaurantExists(Long restaurantId);
    boolean orderRated(Long orderId);
    RestaurantRatingResult insert(SubmitRestaurantRatingCommand command);
    Optional<RestaurantRatingResult> find(Long ratingId);
    RestaurantRatingResult updateStatus(Long ratingId, RestaurantRatingStatus status);
    Aggregate aggregate(Long restaurantId, RestaurantRatingStatus status);
    void updateRestaurantAggregate(Long restaurantId, double average, int count);
    List<RestaurantRatingResult> byRestaurant(Long restaurantId, RestaurantRatingStatus status, int limit);
    List<RestaurantRatingResult> byCustomer(Long customerId, int limit);
    List<RestaurantRatingResult> all(int limit);
    RestaurantRatingPage byRestaurantPage(Long restaurantId, RestaurantRatingStatus status, int page, int size);
    RestaurantRatingPage allPage(int page, int size);

    record Aggregate(long count, double average) {}
}
