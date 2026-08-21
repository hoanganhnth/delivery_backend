package com.delivery.promotion_service.entity;

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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "voucher_reservations",
        indexes = {
                @Index(name = "idx_voucher_reservation_expiry", columnList = "state,expires_at"),
                @Index(name = "idx_voucher_reservation_voucher", columnList = "voucher_id,state")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_voucher_reservation_order", columnNames = "order_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherReservation {
    @Id
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "user_principal_id", updatable = false)
    private Long userPrincipalId;

    @Column(name = "voucher_id", nullable = false, updatable = false)
    private Long voucherId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    @Column(name = "subtotal", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "discount_amount", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void createTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    public enum State { RESERVED, COMMITTED, RELEASED, EXPIRED }
}
