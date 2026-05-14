package com.delivery.flashsale_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "flash_sale_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private FlashSaleCampaign campaign;

    @Column(nullable = false)
    private Long restaurantId;

    @Column(nullable = false)
    private Long menuItemId;

    @Column(nullable = false)
    private BigDecimal originalPrice;

    @Column(nullable = false)
    private BigDecimal flashSalePrice;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false)
    @Builder.Default
    private Integer soldQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status; // PENDING, APPROVED, REJECTED

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (soldQuantity == null) soldQuantity = 0;
        if (status == null) status = ItemStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum ItemStatus { PENDING, APPROVED, REJECTED }
}
