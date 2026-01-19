package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.RestaurantBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RestaurantBalanceRepository extends JpaRepository<RestaurantBalance, Long> {

    Optional<RestaurantBalance> findByRestaurantId(Long restaurantId);

    boolean existsByRestaurantId(Long restaurantId);
}
