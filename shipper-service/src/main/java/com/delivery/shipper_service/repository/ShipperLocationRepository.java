package com.delivery.shipper_service.repository;

import com.delivery.shipper_service.entity.ShipperLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipperLocationRepository extends JpaRepository<ShipperLocation, Long> {
    
    Optional<ShipperLocation> findByShipperId(Long shipperId);
    
    @Query("SELECT sl FROM ShipperLocation sl WHERE " +
           "6371 * acos(cos(radians(:lat)) * cos(radians(sl.lat)) * " +
           "cos(radians(sl.lng) - radians(:lng)) + sin(radians(:lat)) * " +
           "sin(radians(sl.lat))) <= :radiusKm")
    List<ShipperLocation> findShippersWithinRadius(@Param("lat") Double lat, 
                                                   @Param("lng") Double lng, 
                                                   @Param("radiusKm") Double radiusKm);
    
    void deleteByShipperId(Long shipperId);
}
