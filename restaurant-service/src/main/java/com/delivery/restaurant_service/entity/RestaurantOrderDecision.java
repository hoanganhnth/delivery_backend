package com.delivery.restaurant_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "restaurant_order_decisions")
@Getter
@Setter
@NoArgsConstructor
public class RestaurantOrderDecision {

    public enum Decision { CONFIRMED, REJECTED }

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Decision decision;

    @Column(name = "payload_fingerprint", updatable = false, length = 64)
    private String payloadFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
