package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.request.RestaurantRatingRequest;
import com.delivery.restaurant_service.dto.response.RestaurantRatingResponse;

import java.util.List;

public interface RestaurantRatingService {
    RestaurantRatingResponse submitRating(Long restaurantId, Long customerId, RestaurantRatingRequest request);
    List<RestaurantRatingResponse> getRestaurantRatings(Long restaurantId);
    List<RestaurantRatingResponse> getMyRatings(Long customerId);
}
