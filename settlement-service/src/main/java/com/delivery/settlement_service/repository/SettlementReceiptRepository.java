package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.SettlementReceipt;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SettlementReceiptRepository extends JpaRepository<SettlementReceipt, UUID> {
    Optional<SettlementReceipt> findByOrderId(Long orderId);

    /**
     * PostgreSQL lets a concurrent claimant wait for the primary-key winner,
     * then returns zero for an exact replay. This avoids turning normal Kafka
     * redelivery across Settlement replicas into a duplicate-key retry.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO settlement_receipts (
                event_id, order_id, delivery_id, payload_fingerprint, created_at
            ) VALUES (
                :eventId, :orderId, :deliveryId, :payloadFingerprint, CURRENT_TIMESTAMP
            ) ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsentPostgres(
            @Param("eventId") UUID eventId,
            @Param("orderId") Long orderId,
            @Param("deliveryId") Long deliveryId,
            @Param("payloadFingerprint") String payloadFingerprint);

    /**
     * H2's PostgreSQL mode does not reliably support the production conflict
     * syntax. This preserves first-claim versus replay semantics for focused
     * H2 tests; PostgreSQL remains the concurrency authority.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO settlement_receipts (
                event_id, order_id, delivery_id, payload_fingerprint, created_at
            ) SELECT
                :eventId, :orderId, :deliveryId, :payloadFingerprint, CURRENT_TIMESTAMP
            WHERE NOT EXISTS (
                SELECT 1 FROM settlement_receipts WHERE event_id = :eventId
            )
            """, nativeQuery = true)
    int insertIfAbsentH2(
            @Param("eventId") UUID eventId,
            @Param("orderId") Long orderId,
            @Param("deliveryId") Long deliveryId,
            @Param("payloadFingerprint") String payloadFingerprint);
}
