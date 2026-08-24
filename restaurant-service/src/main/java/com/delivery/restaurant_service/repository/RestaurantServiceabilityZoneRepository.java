package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.RestaurantServiceabilityZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantServiceabilityZoneRepository
        extends JpaRepository<RestaurantServiceabilityZone, Long> {

    List<RestaurantServiceabilityZone> findByRestaurantIdOrderByPriorityDescIdAsc(Long restaurantId);
}
