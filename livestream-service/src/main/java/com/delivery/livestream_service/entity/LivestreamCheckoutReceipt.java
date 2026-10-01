package com.delivery.livestream_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Durable, immutable result for one internal livestream checkout handoff. */
@Entity
@Table(name = "livestream_checkout_receipts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_livestream_checkout_receipt_actor_key",
                columnNames = {"actor_principal_id", "idempotency_key"})
})
@Getter
@NoArgsConstructor
public class LivestreamCheckoutReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_principal_id", nullable = false, updatable = false)
    private Long actorPrincipalId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "context_payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String contextPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LivestreamCheckoutReceipt(Long actorPrincipalId, String idempotencyKey,
                                     String requestFingerprint, String contextPayload) {
        this.actorPrincipalId = actorPrincipalId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.contextPayload = contextPayload;
        this.createdAt = Instant.now();
    }
}
