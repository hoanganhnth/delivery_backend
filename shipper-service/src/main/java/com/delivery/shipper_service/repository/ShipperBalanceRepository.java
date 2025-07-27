package com.delivery.shipper_service.repository;

import com.delivery.shipper_service.entity.ShipperBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShipperBalanceRepository extends JpaRepository<ShipperBalance, Long> {
    
    Optional<ShipperBalance> findByShipperId(Long shipperId);
    
    @Query("SELECT sb FROM ShipperBalance sb WHERE sb.balance >= :minBalance")
    List<ShipperBalance> findByBalanceGreaterThanEqual(@Param("minBalance") BigDecimal minBalance);
    
    @Query("SELECT sb FROM ShipperBalance sb WHERE sb.holdingBalance > 0")
    List<ShipperBalance> findShippersWithHoldingBalance();
    
    void deleteByShipperId(Long shipperId);
}
