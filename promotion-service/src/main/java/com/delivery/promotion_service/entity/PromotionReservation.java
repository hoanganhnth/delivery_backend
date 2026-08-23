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
@Table(name = "promotion_reservations",
        indexes = @Index(name = "idx_promotion_reservation_expiry", columnList = "state,expires_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_promotion_reservation_order", columnNames = "order_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionReservation {
    @Id
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "user_principal_id", updatable = false)
    private Long userPrincipalId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    @Column(name = "subtotal", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "gross_shipping_fee", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal grossShippingFee;

    @Column(name = "item_discount", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal itemDiscount;

    @Column(name = "shipping_discount", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal shippingDiscount;

    @Column(name = "total_discount", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal totalDiscount;

    @Column(name = "customer_shipping_fee", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal customerShippingFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
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
