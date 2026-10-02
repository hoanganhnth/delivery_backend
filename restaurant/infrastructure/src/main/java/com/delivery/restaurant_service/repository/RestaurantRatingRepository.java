package com.delivery.restaurant_service.repository;
// Package retained during the infrastructure migration to preserve existing callers.

import com.delivery.restaurant_service.entity.RestaurantRating;
import com.delivery.restaurant.infrastructure.rating.RestaurantRatingAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantRatingRepository extends JpaRepository<RestaurantRating, Long> {
    
    List<RestaurantRating> findByRestaurantIdAndStatus(
            Long restaurantId, com.delivery.restaurant_service.entity.RatingStatus status, Pageable pageable);
    Page<RestaurantRating> findPageByRestaurantIdAndStatus(
            Long restaurantId, com.delivery.restaurant_service.entity.RatingStatus status, Pageable pageable);
    List<RestaurantRating> findByCustomerId(Long customerId, Pageable pageable);
    Page<RestaurantRating> findPageByCustomerId(Long customerId, Pageable pageable);

    @Query("select new com.delivery.restaurant.infrastructure.rating.RestaurantRatingAggregate(" +
            "count(r), coalesce(avg(r.rating), 0.0)) " +
            "from RestaurantRating r " +
            "where r.restaurantId = :restaurantId and r.status = :status")
    RestaurantRatingAggregate aggregateByRestaurantAndStatus(
            @Param("restaurantId") Long restaurantId,
            @Param("status") com.delivery.restaurant_service.entity.RatingStatus status);
    
    boolean existsByOrderId(Long orderId);
}
