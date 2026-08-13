package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.entity.LocationHistoryReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface LocationHistoryReceiptRepository
        extends JpaRepository<LocationHistoryReceipt, UUID> {

    /**
     * PostgreSQL waits for an in-flight primary-key claimant and returns zero
     * after it commits. The losing Kafka replica can then reload the committed
     * receipt as an exact no-op without a rollback-only duplicate-key error.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO location_history_receipts (
                event_id, delivery_id, shipper_id, occurred_at, outcome, processed_at, payload_fingerprint
            ) VALUES (
                :eventId, :deliveryId, :shipperId, :occurredAt, :outcome, CURRENT_TIMESTAMP, :payloadFingerprint
            ) ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int claimIfAbsentPostgres(
            @Param("eventId") UUID eventId,
            @Param("deliveryId") Long deliveryId,
            @Param("shipperId") Long shipperId,
            @Param("occurredAt") Instant occurredAt,
            @Param("outcome") String outcome,
            @Param("payloadFingerprint") String payloadFingerprint);

    /** H2 fallback for focused tests; production uses PostgreSQL ON CONFLICT. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO location_history_receipts (
                event_id, delivery_id, shipper_id, occurred_at, outcome, processed_at, payload_fingerprint
            ) SELECT
                :eventId, :deliveryId, :shipperId, :occurredAt, :outcome, CURRENT_TIMESTAMP, :payloadFingerprint
            WHERE NOT EXISTS (
                SELECT 1 FROM location_history_receipts WHERE event_id = :eventId
            )
            """, nativeQuery = true)
    int claimIfAbsentH2(
            @Param("eventId") UUID eventId,
            @Param("deliveryId") Long deliveryId,
            @Param("shipperId") Long shipperId,
            @Param("occurredAt") Instant occurredAt,
            @Param("outcome") String outcome,
            @Param("payloadFingerprint") String payloadFingerprint);

    @Modifying(flushAutomatically = true)
    @Query("update LocationHistoryReceipt r set r.outcome = :outcome "
            + "where r.eventId = :eventId and r.outcome = "
            + "com.delivery.tracking_service.entity.LocationHistoryReceipt.Outcome.PENDING")
    int completeClaim(@Param("eventId") UUID eventId,
                      @Param("outcome") LocationHistoryReceipt.Outcome outcome);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LocationHistoryReceipt r where r.processedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
