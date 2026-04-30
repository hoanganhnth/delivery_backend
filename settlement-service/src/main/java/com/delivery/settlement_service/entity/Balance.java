package com.delivery.settlement_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Balance entity - Projection/Read-Model
 * This is NOT the source of truth. Can be rebuilt from Transaction history.
 *
 * ✅ Mô hình 2 Ví cho Shipper:
 * - availableBalance = Ví Thu nhập (Earnings): Tiền công giao hàng, thưởng. Rút về ngân hàng.
 * - depositBalance   = Ví Ký quỹ (Deposit):  Shipper nạp trước. App trừ chiết khấu & đối trừ COD.
 *
 * Nhà hàng chỉ dùng availableBalance.
 */
@Entity
@Table(name = "balances", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"entity_id", "entity_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Balance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    /**
     * Ví Thu nhập (Earnings Wallet)
     * Chứa tiền công giao hàng, thưởng. Shipper/Nhà hàng rút về ngân hàng.
     */
    @Builder.Default
    @Column(name = "available_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "pending_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "holding_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal holdingBalance = BigDecimal.ZERO;

    /**
     * Ví Ký quỹ (Deposit Wallet) — Chỉ dùng cho Shipper
     * Shipper nạp trước. App trừ tiền COD thu hộ vào đây.
     */
    @Builder.Default
    @Column(name = "deposit_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositBalance = BigDecimal.ZERO;

    /**
     * Tổng tiền shipper đã nạp vào ví ký quỹ (thống kê, không bao giờ giảm)
     */
    @Builder.Default
    @Column(name = "total_deposited", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeposited = BigDecimal.ZERO;

    /**
     * Tổng tiền COD shipper đã thu (thống kê)
     */
    @Builder.Default
    @Column(name = "total_cod_collected", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCodCollected = BigDecimal.ZERO;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
