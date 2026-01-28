package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.EntityType;
import com.delivery.settlement_service.entity.Transaction;
import com.delivery.settlement_service.entity.Transaction.TransactionReason;
import com.delivery.settlement_service.entity.Transaction.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByEntityIdAndEntityTypeOrderByCreatedAtDesc(Long entityId, EntityType entityType);

    List<Transaction> findByEntityIdAndEntityTypeAndReasonOrderByCreatedAtDesc(
            Long entityId, EntityType entityType, TransactionReason reason);

    List<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status);

    List<Transaction> findAllByOrderByCreatedAtDesc();

    List<Transaction> findByEntityIdAndEntityTypeAndStatus(Long entityId, EntityType entityType, TransactionStatus status);

    /**
     * Calculate available balance from transactions
     * CREDIT increases balance, DEBIT decreases balance
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN t.direction = 'CREDIT' THEN t.amount ELSE -t.amount END), 0) " +
           "FROM Transaction t WHERE t.entityId = :entityId AND t.entityType = :entityType " +
           "AND t.status = 'COMPLETED'")
    BigDecimal calculateAvailableBalance(@Param("entityId") Long entityId, 
                                         @Param("entityType") EntityType entityType);

    /**
     * Calculate total platform revenue (commission)
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.reason = 'PLATFORM_COMMISSION' AND t.status = 'COMPLETED'")
    BigDecimal calculateTotalPlatformRevenue();

    /**
     * Calculate total earnings for an entity type
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.entityType = :entityType AND t.direction = 'CREDIT' " +
           "AND t.reason IN ('ORDER_EARNING', 'DELIVERY_FEE') AND t.status = 'COMPLETED'")
    BigDecimal calculateTotalEarnings(@Param("entityType") EntityType entityType);

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
