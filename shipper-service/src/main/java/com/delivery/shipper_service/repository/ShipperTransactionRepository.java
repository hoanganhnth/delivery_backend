package com.delivery.shipper_service.repository;

import com.delivery.shipper_service.entity.ShipperTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShipperTransactionRepository extends JpaRepository<ShipperTransaction, Long> {
    
    List<ShipperTransaction> findByShipperIdOrderByCreatedAtDesc(Long shipperId);
    
    Page<ShipperTransaction> findByShipperIdOrderByCreatedAtDesc(Long shipperId, Pageable pageable);
    
    List<ShipperTransaction> findByShipperIdAndTransactionType(Long shipperId, 
                                                              ShipperTransaction.TransactionType transactionType);
    
    List<ShipperTransaction> findByRelatedOrderId(Long orderId);
    
    @Query("SELECT st FROM ShipperTransaction st WHERE st.shipperId = :shipperId " +
           "AND st.createdAt BETWEEN :startDate AND :endDate")
    List<ShipperTransaction> findByShipperIdAndDateRange(@Param("shipperId") Long shipperId,
                                                        @Param("startDate") LocalDateTime startDate,
                                                        @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(st.amount) FROM ShipperTransaction st WHERE st.shipperId = :shipperId " +
           "AND st.transactionType = :transactionType")
    BigDecimal sumAmountByShipperIdAndTransactionType(@Param("shipperId") Long shipperId,
                                                     @Param("transactionType") ShipperTransaction.TransactionType transactionType);
}
