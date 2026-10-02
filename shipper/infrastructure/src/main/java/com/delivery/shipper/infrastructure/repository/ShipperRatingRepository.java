package com.delivery.shipper.infrastructure.repository;

import com.delivery.shipper.infrastructure.entity.ShipperRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface ShipperRatingRepository extends JpaRepository<ShipperRating, Long> {
    List<ShipperRating> findByShipperIdOrderByCreatedAtDesc(Long shipperId, Pageable pageable);

    @Query("select avg(r.rating) from ShipperRating r where r.shipperId = :shipperId")
    Double findAverageRatingByShipperId(@Param("shipperId") Long shipperId);

    boolean existsByOrderId(Long orderId);

    long countByShipperId(Long shipperId);
}
