package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.RestaurantRating;
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

    long countByRestaurantIdAndStatus(
            Long restaurantId, com.delivery.restaurant_service.entity.RatingStatus status);

    @Query("select coalesce(avg(r.rating), 0) from RestaurantRating r " +
            "where r.restaurantId = :restaurantId and r.status = :status")
    Double averageRatingByRestaurantAndStatus(
            @Param("restaurantId") Long restaurantId,
            @Param("status") com.delivery.restaurant_service.entity.RatingStatus status);
    
    boolean existsByOrderId(Long orderId);
}
