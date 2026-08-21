package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.entity.ShipperIdentityProjection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipperIdentityProjectionRepository extends JpaRepository<ShipperIdentityProjection, Long> {}
