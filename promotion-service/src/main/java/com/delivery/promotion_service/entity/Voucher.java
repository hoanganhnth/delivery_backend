package com.delivery.promotion_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreatorType creatorType;

    // null if PLATFORM, shop_id if MERCHANT
    private Long creatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RewardType rewardType;

    @Column(nullable = false)
    private BigDecimal discountValue;

    private BigDecimal maxDiscountValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScopeType scopeType;

    // e.g., ShopId or CategoryId
    private Long scopeRefId;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    @Builder.Default
    private Integer usedQuantity = 0;

    @Column(nullable = false)
    private Integer usageLimitPerUser;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    private BigDecimal minOrderValue;

    // NEW: to manage stacking dynamically
    private Long voucherGroupId;

    private String customerSegment;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum CreatorType {
        PLATFORM, MERCHANT
    }

    public enum RewardType {
        FIXED, PERCENTAGE, FREESHIP
    }

    public enum ScopeType {
        ALL, SHOP, CATEGORY
    }
}
