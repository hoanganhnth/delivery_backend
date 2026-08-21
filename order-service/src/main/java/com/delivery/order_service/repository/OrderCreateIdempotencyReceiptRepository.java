package com.delivery.order_service.repository;

import com.delivery.order_service.entity.OrderCreateIdempotencyReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderCreateIdempotencyReceiptRepository
        extends JpaRepository<OrderCreateIdempotencyReceipt, Long> {

    Optional<OrderCreateIdempotencyReceipt> findByPrincipalIdAndIdempotencyKey(
            Long principalId, UUID idempotencyKey);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO order_create_idempotency_receipts
                (principal_id, idempotency_key, request_fingerprint, fingerprint_version, created_at)
            VALUES (:principalId, :idempotencyKey, :requestFingerprint, :fingerprintVersion, CURRENT_TIMESTAMP)
            ON CONFLICT (principal_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsentPostgres(@Param("principalId") Long principalId,
                               @Param("idempotencyKey") UUID idempotencyKey,
                               @Param("requestFingerprint") String requestFingerprint,
                               @Param("fingerprintVersion") String fingerprintVersion);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO order_create_idempotency_receipts
                (principal_id, idempotency_key, request_fingerprint, fingerprint_version, created_at)
            SELECT :principalId, :idempotencyKey, :requestFingerprint, :fingerprintVersion, CURRENT_TIMESTAMP
            WHERE NOT EXISTS (
                SELECT 1 FROM order_create_idempotency_receipts
                WHERE principal_id = :principalId AND idempotency_key = :idempotencyKey
            )
            """, nativeQuery = true)
    int insertIfAbsentH2(@Param("principalId") Long principalId,
                         @Param("idempotencyKey") UUID idempotencyKey,
                         @Param("requestFingerprint") String requestFingerprint,
                         @Param("fingerprintVersion") String fingerprintVersion);

    long deleteByCreatedAtBefore(Instant cutoff);
}
