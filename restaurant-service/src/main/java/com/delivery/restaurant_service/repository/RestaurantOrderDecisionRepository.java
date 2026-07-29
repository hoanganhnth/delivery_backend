package com.delivery.restaurant_service.repository;

import com.delivery.restaurant_service.entity.RestaurantOrderDecision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RestaurantOrderDecisionRepository extends JpaRepository<RestaurantOrderDecision, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from RestaurantOrderDecision d where d.orderId = :orderId")
    Optional<RestaurantOrderDecision> findByOrderIdForUpdate(@Param("orderId") Long orderId);

}
