package com.delivery.flashsale_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "flash_sale_reservations",
        indexes = @Index(name = "idx_flash_reservation_expiry", columnList = "state,expires_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_flash_reservation_order", columnNames = "order_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FlashSaleReservation {
    @Id @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;
    @Column(name = "order_id", nullable = false, updatable = false) private Long orderId;
    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(name = "restaurant_id", nullable = false, updatable = false) private Long restaurantId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private State state;
    @Column(name = "expires_at", nullable = false, updatable = false) private LocalDateTime expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default private List<FlashSaleReservationLine> lines = new ArrayList<>();

    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public enum State { RESERVED, COMMITTED, RELEASED, EXPIRED }
}
