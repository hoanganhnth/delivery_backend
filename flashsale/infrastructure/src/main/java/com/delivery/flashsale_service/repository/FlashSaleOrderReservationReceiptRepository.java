package com.delivery.flashsale_service.repository;

import com.delivery.flashsale_service.entity.FlashSaleOrderReservationReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface FlashSaleOrderReservationReceiptRepository
        extends JpaRepository<FlashSaleOrderReservationReceipt, UUID> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO flash_sale_order_reservation_receipts (
                event_id, source_topic, action, order_id, reservation_id,
                payload_fingerprint, created_at
            ) VALUES (
                :eventId, :sourceTopic, :action, :orderId, :reservationId,
                :payloadFingerprint, CURRENT_TIMESTAMP
            ) ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsentPostgres(
            @Param("eventId") UUID eventId,
            @Param("sourceTopic") String sourceTopic,
            @Param("action") String action,
            @Param("orderId") Long orderId,
            @Param("reservationId") UUID reservationId,
            @Param("payloadFingerprint") String payloadFingerprint);

    /** H2 fallback for focused tests; PostgreSQL is the production race authority. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO flash_sale_order_reservation_receipts (
                event_id, source_topic, action, order_id, reservation_id,
                payload_fingerprint, created_at
            ) SELECT
                :eventId, :sourceTopic, :action, :orderId, :reservationId,
                :payloadFingerprint, CURRENT_TIMESTAMP
            WHERE NOT EXISTS (
                SELECT 1 FROM flash_sale_order_reservation_receipts WHERE event_id = :eventId
            )
            """, nativeQuery = true)
    int insertIfAbsentH2(
            @Param("eventId") UUID eventId,
            @Param("sourceTopic") String sourceTopic,
            @Param("action") String action,
            @Param("orderId") Long orderId,
            @Param("reservationId") UUID reservationId,
            @Param("payloadFingerprint") String payloadFingerprint);
}
