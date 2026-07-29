package com.delivery.shipper_service.repository;

import com.delivery.shipper_service.entity.Shipper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipperRepository extends JpaRepository<Shipper, Long> {
    Optional<Shipper> findByUserId(Long userId);
    List<Shipper> findByIsOnline(Boolean isOnline, Pageable pageable);
    boolean existsByLicenseNumber(String licenseNumber);
    boolean existsByIdCard(String idCard);
}
