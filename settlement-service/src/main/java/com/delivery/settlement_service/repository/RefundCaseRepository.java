package com.delivery.settlement_service.repository;

import com.delivery.settlement_service.entity.RefundCase;
import com.delivery.settlement_service.entity.RefundCase.RefundComponent;
import com.delivery.settlement_service.entity.RefundCase.RefundStatus;
import com.delivery.settlement_service.entity.RefundCase.RefundTrigger;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundCaseRepository extends JpaRepository<RefundCase, UUID> {
    Optional<RefundCase> findByEventId(UUID eventId);

    Optional<RefundCase> findByIdempotencyKey(String idempotencyKey);

    Optional<RefundCase> findByOrderIdAndTriggerAndComponent(
            Long orderId, RefundTrigger trigger, RefundComponent component);

    /**
     * PostgreSQL serializes a concurrent claimant on any of the refund case's
     * identity constraints. The losing consumer observes zero, then verifies
     * whether it was an exact replay or a conflicting refund trigger.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO refund_cases (
                refund_id, event_id, idempotency_key, order_id, user_id, restaurant_id,
                previous_order_status, current_order_status, payment_method,
                refund_trigger, component, status, currency, subtotal_amount,
                discount_amount, shipping_fee, total_amount, captured_amount,
                refund_amount, actor_source, actor_id, reason, payload_fingerprint,
                attempts, created_at, updated_at
            ) VALUES (
                :refundId, :eventId, :idempotencyKey, :orderId, :userId, :restaurantId,
                :previousOrderStatus, :currentOrderStatus, :paymentMethod,
                :trigger, :component, :status, :currency, :subtotalAmount,
                :discountAmount, :shippingFee, :totalAmount, :capturedAmount,
                :refundAmount, :actorSource, :actorId, :reason, :payloadFingerprint,
                :attempts, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            ) ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsentPostgres(
            @Param("refundId") UUID refundId,
            @Param("eventId") UUID eventId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("orderId") Long orderId,
            @Param("userId") Long userId,
            @Param("restaurantId") Long restaurantId,
            @Param("previousOrderStatus") String previousOrderStatus,
            @Param("currentOrderStatus") String currentOrderStatus,
            @Param("paymentMethod") String paymentMethod,
            @Param("trigger") String trigger,
            @Param("component") String component,
            @Param("status") String status,
            @Param("currency") String currency,
            @Param("subtotalAmount") BigDecimal subtotalAmount,
            @Param("discountAmount") BigDecimal discountAmount,
            @Param("shippingFee") BigDecimal shippingFee,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("capturedAmount") BigDecimal capturedAmount,
            @Param("refundAmount") BigDecimal refundAmount,
            @Param("actorSource") String actorSource,
            @Param("actorId") Long actorId,
            @Param("reason") String reason,
            @Param("payloadFingerprint") String payloadFingerprint,
            @Param("attempts") int attempts);

    /**
     * H2 does not support the PostgreSQL conflict syntax used in production.
     * This sequential fallback keeps H2 transaction tests readable while the
     * PostgreSQL path remains the authoritative concurrent-consumer boundary.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO refund_cases (
                refund_id, event_id, idempotency_key, order_id, user_id, restaurant_id,
                previous_order_status, current_order_status, payment_method,
                refund_trigger, component, status, currency, subtotal_amount,
                discount_amount, shipping_fee, total_amount, captured_amount,
                refund_amount, actor_source, actor_id, reason, payload_fingerprint,
                attempts, created_at, updated_at
            ) SELECT
                :refundId, :eventId, :idempotencyKey, :orderId, :userId, :restaurantId,
                :previousOrderStatus, :currentOrderStatus, :paymentMethod,
                :trigger, :component, :status, :currency, :subtotalAmount,
                :discountAmount, :shippingFee, :totalAmount, :capturedAmount,
                :refundAmount, :actorSource, :actorId, :reason, :payloadFingerprint,
                :attempts, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            WHERE NOT EXISTS (
                SELECT 1 FROM refund_cases
                WHERE event_id = :eventId
                   OR idempotency_key = :idempotencyKey
                   OR (order_id = :orderId
                       AND refund_trigger = :trigger
                       AND component = :component)
            )
            """, nativeQuery = true)
    int insertIfAbsentH2(
            @Param("refundId") UUID refundId,
            @Param("eventId") UUID eventId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("orderId") Long orderId,
            @Param("userId") Long userId,
            @Param("restaurantId") Long restaurantId,
            @Param("previousOrderStatus") String previousOrderStatus,
            @Param("currentOrderStatus") String currentOrderStatus,
            @Param("paymentMethod") String paymentMethod,
            @Param("trigger") String trigger,
            @Param("component") String component,
            @Param("status") String status,
            @Param("currency") String currency,
            @Param("subtotalAmount") BigDecimal subtotalAmount,
            @Param("discountAmount") BigDecimal discountAmount,
            @Param("shippingFee") BigDecimal shippingFee,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("capturedAmount") BigDecimal capturedAmount,
            @Param("refundAmount") BigDecimal refundAmount,
            @Param("actorSource") String actorSource,
            @Param("actorId") Long actorId,
            @Param("reason") String reason,
            @Param("payloadFingerprint") String payloadFingerprint,
            @Param("attempts") int attempts);

    List<RefundCase> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<RefundCase> findByStatusOrderByCreatedAtDesc(RefundStatus status, Pageable pageable);

    List<RefundCase> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
