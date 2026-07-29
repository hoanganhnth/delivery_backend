package com.delivery.restaurant_service.service.impl;

import com.delivery.restaurant_service.dto.request.RestaurantRatingRequest;
import com.delivery.restaurant_service.dto.response.RestaurantRatingResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.RestaurantRating;
import com.delivery.restaurant_service.exception.RestaurantRatingConflictException;
import com.delivery.restaurant_service.repository.RestaurantRatingRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import com.delivery.restaurant_service.service.RestaurantRatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RestaurantRatingServiceImpl implements RestaurantRatingService {

    private final RestaurantRatingRepository ratingRepository;
    private final RestaurantRepository restaurantRepository;

    @Override
    @Transactional
    public RestaurantRatingResponse submitRating(Long restaurantId, Long customerId, RestaurantRatingRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found with ID: " + restaurantId));

        if (ratingRepository.existsByOrderId(request.getOrderId())) {
            throw new RestaurantRatingConflictException("Order has already been rated for this restaurant");
        }

        RestaurantRating rating = new RestaurantRating();
        rating.setRestaurantId(restaurantId);
        rating.setCustomerId(customerId);
        rating.setOrderId(request.getOrderId());
        rating.setRating(request.getRating());
        rating.setComment(request.getComment());

        try {
            rating = ratingRepository.saveAndFlush(rating);
        } catch (DataIntegrityViolationException duplicate) {
            throw new RestaurantRatingConflictException(
                    "Order has already been rated for this restaurant");
        }

        updateRestaurantAverageRating(restaurant);

        return mapToResponse(rating);
    }


    @Override
    public List<RestaurantRatingResponse> getRestaurantRatings(Long restaurantId) {
        return ratingRepository.findByRestaurantIdAndStatus(
                        restaurantId, com.delivery.restaurant_service.entity.RatingStatus.APPROVED,
                        PageRequest.of(0, 100)).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantRatingResponse> getMyRatings(Long customerId) {
        return ratingRepository.findByCustomerId(customerId, PageRequest.of(0, 100)).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<RestaurantRatingResponse> getAllRatings() {
        return ratingRepository.findAll(PageRequest.of(0, 100)).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RestaurantRatingResponse updateRatingStatus(Long ratingId, String status) {
        RestaurantRating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new IllegalArgumentException("Rating not found with ID: " + ratingId));

        com.delivery.restaurant_service.entity.RatingStatus newStatus = com.delivery.restaurant_service.entity.RatingStatus.valueOf(status.toUpperCase());
        rating.setStatus(newStatus);
        rating = ratingRepository.save(rating);

        // Re-calculate average rating if status changed to/from APPROVED
        Restaurant restaurant = restaurantRepository.findById(rating.getRestaurantId())
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
        updateRestaurantAverageRating(restaurant);

        return mapToResponse(rating);
    }

    private void updateRestaurantAverageRating(Restaurant restaurant) {
        var approved = com.delivery.restaurant_service.entity.RatingStatus.APPROVED;
        long count = ratingRepository.countByRestaurantIdAndStatus(restaurant.getId(), approved);
        Double average = ratingRepository.averageRatingByRestaurantAndStatus(restaurant.getId(), approved);
        restaurant.setRating(count == 0 ? 0.0 : average);
        restaurant.setRatingCount(Math.toIntExact(count));
        restaurantRepository.save(restaurant);
    }

    private RestaurantRatingResponse mapToResponse(RestaurantRating rating) {
        RestaurantRatingResponse response = new RestaurantRatingResponse();
        response.setId(rating.getId());
        response.setRestaurantId(rating.getRestaurantId());
        response.setCustomerId(rating.getCustomerId());
        response.setOrderId(rating.getOrderId());
        response.setRating(rating.getRating());
        response.setComment(rating.getComment());
        response.setStatus(rating.getStatus().name());
        response.setCreatedAt(rating.getCreatedAt());
        return response;
    }
}
