package com.delivery.restaurant.infrastructure.rating;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.rating.RestaurantRatingConflictException;
import com.delivery.restaurant.domain.rating.RestaurantRatingStatus;
import com.delivery.restaurant_service.entity.RatingStatus;
import com.delivery.restaurant_service.entity.RestaurantRating;
import com.delivery.restaurant_service.repository.RestaurantRatingRepository;
import com.delivery.restaurant_service.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/** Database mapping, flushing and transaction-scoped PostgreSQL advisory lock. */
@Component
@RequiredArgsConstructor
public class JpaRestaurantRatingAdapter implements RestaurantRatingStorePort {
    private final RestaurantRatingRepository ratingRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantRatingLock ratingLock;

    @Override public void lockRestaurant(Long id) { ratingLock.lock(id); }
    @Override public boolean restaurantExists(Long id) { return restaurantRepository.findById(id).isPresent(); }
    @Override public boolean orderRated(Long id) { return ratingRepository.existsByOrderId(id); }
    @Override public Optional<RestaurantRatingResult> find(Long id) { return ratingRepository.findById(id).map(this::map); }
    @Override public RestaurantRatingResult insert(SubmitRestaurantRatingCommand c) {
        RestaurantRating rating = new RestaurantRating();
        rating.setRestaurantId(c.restaurantId()); rating.setCustomerId(c.customerId());
        rating.setOrderId(c.orderId()); rating.setRating(c.rating()); rating.setComment(c.comment());
        try { return map(ratingRepository.saveAndFlush(rating)); }
        catch (DataIntegrityViolationException duplicate) {
            throw new RestaurantRatingConflictException("Order has already been rated for this restaurant");
        }
    }
    @Override public RestaurantRatingResult updateStatus(Long id, RestaurantRatingStatus status) {
        var rating = ratingRepository.findById(id).orElseThrow();
        rating.setStatus(RatingStatus.valueOf(status.name()));
        return map(ratingRepository.saveAndFlush(rating));
    }
    @Override public Aggregate aggregate(Long id, RestaurantRatingStatus status) {
        var result = ratingRepository.aggregateByRestaurantAndStatus(id, RatingStatus.valueOf(status.name()));
        return new Aggregate(result.count(), result.average());
    }
    @Override public void updateRestaurantAggregate(Long id, double average, int count) {
        var restaurant = restaurantRepository.findById(id).orElseThrow();
        restaurant.setRating(average); restaurant.setRatingCount(count);
        restaurantRepository.save(restaurant);
    }
    @Override public List<RestaurantRatingResult> byRestaurant(Long id, RestaurantRatingStatus status, int limit) {
        return ratingRepository.findByRestaurantIdAndStatus(id, RatingStatus.valueOf(status.name()), PageRequest.of(0, limit))
                .stream().map(this::map).toList();
    }
    @Override public List<RestaurantRatingResult> byCustomer(Long id, int limit) {
        return ratingRepository.findByCustomerId(id, PageRequest.of(0, limit)).stream().map(this::map).toList();
    }
    @Override public List<RestaurantRatingResult> all(int limit) {
        return ratingRepository.findAll(PageRequest.of(0, limit)).stream().map(this::map).toList();
    }
    @Override public RestaurantRatingPage byRestaurantPage(Long id, RestaurantRatingStatus status, int page, int size) {
        return page(ratingRepository.findPageByRestaurantIdAndStatus(id, RatingStatus.valueOf(status.name()), PageRequest.of(page, size)));
    }
    @Override public RestaurantRatingPage allPage(int page, int size) {
        return page(ratingRepository.findAll(PageRequest.of(page, size)));
    }
    private RestaurantRatingPage page(Page<RestaurantRating> source) {
        return new RestaurantRatingPage(source.getContent().stream().map(this::map).toList(),
                source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages(), source.hasNext());
    }
    private RestaurantRatingResult map(RestaurantRating rating) {
        return new RestaurantRatingResult(rating.getId(), rating.getRestaurantId(), rating.getCustomerId(),
                rating.getOrderId(), rating.getRating(), rating.getComment(), rating.getStatus().name(), rating.getCreatedAt());
    }
}
