package com.delivery.shipper_service.repository;

import com.delivery.shipper_service.entity.ShipperRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipperRatingRepository extends JpaRepository<ShipperRating, Long> {
    List<ShipperRating> findByShipperIdOrderByCreatedAtDesc(Long shipperId);
    boolean existsByOrderId(Long orderId);
}
