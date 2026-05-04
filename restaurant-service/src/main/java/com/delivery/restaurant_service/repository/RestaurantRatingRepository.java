package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.RestaurantRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRatingRepository extends JpaRepository<RestaurantRating, Long> {
    
    List<RestaurantRating> findByRestaurantId(Long restaurantId);
    
    List<RestaurantRating> findByRestaurantIdAndStatus(Long restaurantId, com.delivery.restaurant_service.entity.RatingStatus status);
    
    List<RestaurantRating> findByStatus(com.delivery.restaurant_service.entity.RatingStatus status);
    
    List<RestaurantRating> findByCustomerId(Long customerId);
    
    Optional<RestaurantRating> findByOrderId(Long orderId);
    
    boolean existsByOrderId(Long orderId);
}
