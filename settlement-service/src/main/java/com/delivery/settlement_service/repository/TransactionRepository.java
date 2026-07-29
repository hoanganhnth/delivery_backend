package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByEntityIdAndEntityTypeOrderByCreatedAtDesc(
            Long entityId, EntityType entityType, Pageable pageable);

    List<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status, Pageable pageable);

    List<Transaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * ✅ Idempotency check — kiểm tra orderId đã được xử lý chưa
     * Dùng để tránh cộng tiền 2 lần khi Kafka retry
     */
    boolean existsByOrderIdAndEntityIdAndEntityTypeAndReason(
            Long orderId, Long entityId, EntityType entityType, TransactionReason reason);

    /**
     * Calculate total platform revenue (commission)
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.reason = 'PLATFORM_COMMISSION' AND t.status = 'COMPLETED'")
    BigDecimal calculateTotalPlatformRevenue();

    /**
     * Calculate total earnings for a specific entity
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.entityType = :entityType " +
           "AND t.direction = 'CREDIT' AND t.reason IN ('ORDER_EARNING', 'DELIVERY_FEE') " +
           "AND t.status = 'COMPLETED'")
    BigDecimal calculateEntityTotalEarnings(@Param("entityId") Long entityId, 
                                           @Param("entityType") EntityType entityType);
}
