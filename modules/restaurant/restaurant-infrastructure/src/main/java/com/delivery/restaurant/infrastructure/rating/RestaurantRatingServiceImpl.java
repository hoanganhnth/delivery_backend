package com.delivery.restaurant.infrastructure.rating;

import com.delivery.restaurant.application.api.RestaurantRatingPage;
import com.delivery.restaurant.application.api.RestaurantRatingResult;
import com.delivery.restaurant.application.api.RestaurantRatingUseCase;
import com.delivery.restaurant.application.api.SubmitRestaurantRatingCommand;
import com.delivery.restaurant.infrastructure.client.OrderEligibilityPort;
import com.delivery.restaurant_service.entity.RatingStatus;
import com.delivery.restaurant_service.entity.Restaurant;
import com.delivery.restaurant_service.entity.RestaurantRating;
import com.delivery.restaurant_service.repository.RestaurantRatingRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PostgreSQL-backed rating workflow. The host owns only HTTP DTO mapping;
 * persistence and transaction coordination live in infrastructure.
 */
@Service
@RequiredArgsConstructor
public class RestaurantRatingServiceImpl implements RestaurantRatingUseCase {

    private static final String DUPLICATE_RATING_MESSAGE =
            "Order has already been rated for this restaurant";

    private final RestaurantRatingRepository ratingRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderEligibilityPort orderEligibilityPort;
    private final RestaurantRatingLock ratingLock;

    @Override
    @Transactional
    public RestaurantRatingResult submitRating(SubmitRestaurantRatingCommand command) {
        orderEligibilityPort.requireDeliveredOrder(
                command.orderId(), command.customerId(), command.restaurantId());

        // A JPA PESSIMISTIC_WRITE lock was not a sufficient application
        // boundary for this hot aggregate. The advisory lock is held until
        // commit and covers the duplicate check, insert and aggregate update.
        ratingLock.lock(command.restaurantId());
        Restaurant restaurant = restaurantRepository.findById(command.restaurantId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Restaurant not found with ID: " + command.restaurantId()));

        if (ratingRepository.existsByOrderId(command.orderId())) {
            throw new RestaurantRatingConflictException(DUPLICATE_RATING_MESSAGE);
        }

        RestaurantRating rating = new RestaurantRating();
        rating.setRestaurantId(command.restaurantId());
        rating.setCustomerId(command.customerId());
        rating.setOrderId(command.orderId());
        rating.setRating(command.rating());
        rating.setComment(command.comment());

        try {
            rating = ratingRepository.saveAndFlush(rating);
        } catch (DataIntegrityViolationException duplicate) {
            // The unique order_id constraint remains the final race-safe
            // guard if an older writer did not use the application lock.
            throw new RestaurantRatingConflictException(DUPLICATE_RATING_MESSAGE);
        }

        updateRestaurantAverageRating(restaurant);
        return mapToResult(rating);
    }

    @Override
    public List<RestaurantRatingResult> getRestaurantRatings(Long restaurantId) {
        return ratingRepository.findByRestaurantIdAndStatus(
                        restaurantId, RatingStatus.APPROVED, PageRequest.of(0, 100)).stream()
                .map(this::mapToResult)
                .toList();
    }

    @Override
    public List<RestaurantRatingResult> getMyRatings(Long customerId) {
        return ratingRepository.findByCustomerId(customerId, PageRequest.of(0, 100)).stream()
                .map(this::mapToResult)
                .toList();
    }

    @Override
    public List<RestaurantRatingResult> getAllRatings() {
        return ratingRepository.findAll(PageRequest.of(0, 100)).stream()
                .map(this::mapToResult)
                .toList();
    }

    @Override
    public RestaurantRatingPage getRestaurantRatingsPage(Long restaurantId, int page, int size) {
        Page<RestaurantRating> source = ratingRepository.findPageByRestaurantIdAndStatus(
                restaurantId, RatingStatus.APPROVED, PageRequest.of(page, size));
        return mapToPage(source);
    }

    @Override
    public RestaurantRatingPage getAllRatingsPage(int page, int size) {
        return mapToPage(ratingRepository.findAll(PageRequest.of(page, size)));
    }

    @Override
    @Transactional
    public RestaurantRatingResult updateRatingStatus(Long ratingId, String status) {
        RestaurantRating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new IllegalArgumentException("Rating not found with ID: " + ratingId));

        ratingLock.lock(rating.getRestaurantId());
        RatingStatus newStatus = RatingStatus.valueOf(status.toUpperCase());
        rating.setStatus(newStatus);
        rating = ratingRepository.saveAndFlush(rating);

        Restaurant restaurant = restaurantRepository.findById(rating.getRestaurantId())
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
        updateRestaurantAverageRating(restaurant);
        return mapToResult(rating);
    }

    private void updateRestaurantAverageRating(Restaurant restaurant) {
        RestaurantRatingAggregate aggregate = ratingRepository.aggregateByRestaurantAndStatus(
                restaurant.getId(), RatingStatus.APPROVED);
        restaurant.setRating(aggregate.average());
        restaurant.setRatingCount(Math.toIntExact(aggregate.count()));
        restaurantRepository.save(restaurant);
    }

    private RestaurantRatingPage mapToPage(Page<RestaurantRating> source) {
        return new RestaurantRatingPage(
                source.getContent().stream().map(this::mapToResult).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasNext());
    }

    private RestaurantRatingResult mapToResult(RestaurantRating rating) {
        return new RestaurantRatingResult(
                rating.getId(),
                rating.getRestaurantId(),
                rating.getCustomerId(),
                rating.getOrderId(),
                rating.getRating(),
                rating.getComment(),
                rating.getStatus().name(),
                rating.getCreatedAt());
    }
}
