package com.delivery.restaurant.application.api;

import java.util.List;

/** Application boundary for Restaurant rating submission, moderation and reads. */
public interface RestaurantRatingUseCase {

    RestaurantRatingResult submitRating(SubmitRestaurantRatingCommand command);

    List<RestaurantRatingResult> getRestaurantRatings(Long restaurantId);

    List<RestaurantRatingResult> getMyRatings(Long customerId);

    List<RestaurantRatingResult> getAllRatings();

    RestaurantRatingResult updateRatingStatus(Long ratingId, String status);

    RestaurantRatingPage getRestaurantRatingsPage(Long restaurantId, int page, int size);

    RestaurantRatingPage getAllRatingsPage(int page, int size);
}
