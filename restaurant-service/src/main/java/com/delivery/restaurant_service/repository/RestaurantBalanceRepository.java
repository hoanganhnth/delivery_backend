package com.delivery.restaurant_service.repository;


import com.delivery.restaurant_service.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;


public interface RestaurantBalanceRepository extends JpaRepository<MenuItem, Long> {
    // Define methods for interacting with the restaurant balance data
    // For example:
    // Optional<RestaurantBalance> findByRestaurantId(Long restaurantId);


}
