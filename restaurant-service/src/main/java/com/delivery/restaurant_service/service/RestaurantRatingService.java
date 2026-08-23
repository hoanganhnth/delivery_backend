package com.delivery.restaurant_service.service;

import com.delivery.restaurant_service.dto.request.RestaurantRatingRequest;
import com.delivery.restaurant_service.dto.response.RestaurantRatingResponse;

import java.util.List;
import org.springframework.data.domain.Page;

public interface RestaurantRatingService {
    RestaurantRatingResponse submitRating(Long restaurantId, Long customerId, RestaurantRatingRequest request);
    List<RestaurantRatingResponse> getRestaurantRatings(Long restaurantId);
    List<RestaurantRatingResponse> getMyRatings(Long customerId);
    List<RestaurantRatingResponse> getAllRatings();
    RestaurantRatingResponse updateRatingStatus(Long ratingId, String status);
    Page<RestaurantRatingResponse> getRestaurantRatingsPage(Long restaurantId, int page, int size);
    Page<RestaurantRatingResponse> getAllRatingsPage(int page, int size);
}
