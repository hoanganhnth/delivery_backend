package com.delivery.promotion_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "promotion_reservation_lines",
        indexes = @Index(name = "idx_promotion_reservation_line_voucher", columnList = "voucher_id,state"),
        uniqueConstraints = @UniqueConstraint(name = "uk_promotion_reservation_line_voucher",
                columnNames = {"reservation_id", "voucher_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionReservationLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "voucher_id", nullable = false, updatable = false)
    private Long voucherId;

    @Column(name = "voucher_code", nullable = false, updatable = false, length = 50)
    private String voucherCode;

    @Column(name = "layer", nullable = false, updatable = false, length = 32)
    private String layer;

    @Column(name = "funding_source", nullable = false, updatable = false, length = 32)
    private String fundingSource;

    @Column(name = "discount_base", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal discountBase;

    @Column(name = "discount_amount", nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private State state;

    public enum State { RESERVED, COMMITTED, RELEASED, EXPIRED }
}
