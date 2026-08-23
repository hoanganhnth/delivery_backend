package com.delivery.settlement_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cod_capacity_holds", indexes = {
        @Index(name = "idx_cod_capacity_holds_shipper_status", columnList = "shipper_id,status,expires_at"),
        @Index(name = "idx_cod_capacity_holds_delivery", columnList = "delivery_id,status")
})
@Getter
@Setter
@NoArgsConstructor
public class CodCapacityHold {

    @Id
    @Column(name = "hold_id", nullable = false, updatable = false)
    private UUID holdId;

    @Column(name = "offer_id", nullable = false, updatable = false)
    private UUID offerId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private Long deliveryId;

    @Column(name = "shipper_id", nullable = false, updatable = false)
    private Long shipperId;

    @Column(name = "matching_session_id", nullable = false, updatable = false)
    private UUID matchingSessionId;

    @Column(name = "wave_id")
    private UUID waveId;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CodCapacityHoldStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;
}
