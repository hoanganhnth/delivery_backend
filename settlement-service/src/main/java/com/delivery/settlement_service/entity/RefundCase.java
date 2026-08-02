package com.delivery.settlement_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Durable refund decision and monetary snapshot.
 *
 * <p>This entity deliberately does not call a payment provider. Provider
 * execution is a separate, disabled-by-default rollout boundary.</p>
 */
@Entity
@Table(name = "refund_cases", indexes = {
        @Index(name = "idx_refund_cases_order", columnList = "order_id,created_at"),
        @Index(name = "idx_refund_cases_status_created", columnList = "status,created_at,refund_id"),
        @Index(name = "idx_refund_cases_event", columnList = "event_id", unique = true)
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_refund_cases_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_refund_cases_order_trigger_component",
                columnNames = {"order_id", "refund_trigger", "component"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCase {

    @Id
    @Column(name = "refund_id", nullable = false, updatable = false)
    private UUID refundId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    @Column(name = "previous_order_status", nullable = false, updatable = false, length = 32)
    private String previousOrderStatus;

    @Column(name = "current_order_status", nullable = false, updatable = false, length = 32)
    private String currentOrderStatus;

    @Column(name = "payment_method", nullable = false, updatable = false, length = 20)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_trigger", nullable = false, updatable = false, length = 32)
    private RefundTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", nullable = false, updatable = false, length = 32)
    @Builder.Default
    private RefundComponent component = RefundComponent.ORDER_TOTAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RefundStatus status;

    @Column(name = "currency", nullable = false, updatable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal discountAmount;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal shippingFee;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "captured_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal capturedAmount;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "actor_source", nullable = false, length = 32, updatable = false)
    private String actorSource;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(name = "payload_fingerprint", nullable = false, length = 64, updatable = false)
    private String payloadFingerprint;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RefundTrigger {
        ORDER_CANCELLED,
        PAYMENT_FAILED,
        SHIPPER_NOT_FOUND,
        DELIVERY_DISPUTE
    }

    public enum RefundComponent {
        ORDER_TOTAL
    }

    public enum RefundStatus {
        REQUESTED,
        PROCESSING,
        SUCCEEDED,
        PARTIAL,
        FAILED,
        MANUAL_REVIEW,
        NO_REFUND_REQUIRED
    }
}
