package com.delivery.livestream_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "livestream_products")
public class LivestreamProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "livestream_id", nullable = false)
    private UUID livestreamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livestream_id", insertable = false, updatable = false)
    private Livestream livestream;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "price_at_live", precision = 12, scale = 2)
    private BigDecimal priceAtLive;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
