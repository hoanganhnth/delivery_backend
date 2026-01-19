package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.RestaurantTransaction;
import com.delivery.restaurant_service.entity.RestaurantTransaction.TypeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantTransactionRepository extends JpaRepository<RestaurantTransaction, Long> {

    List<RestaurantTransaction> findByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);

    List<RestaurantTransaction> findByRestaurant_IdAndTypeOrderByCreatedAtDesc(Long restaurantId, TypeTransaction type);
}
