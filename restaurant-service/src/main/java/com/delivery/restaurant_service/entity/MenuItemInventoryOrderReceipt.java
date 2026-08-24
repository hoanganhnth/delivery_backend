package com.delivery.restaurant_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Durable event identity fence for inventory commit/release consumers. */
@Entity
@Table(name = "menu_item_inventory_order_receipts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemInventoryOrderReceipt {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "source_topic", nullable = false, updatable = false, length = 255)
    private String sourceTopic;

    @Column(name = "action", nullable = false, updatable = false, length = 16)
    private String action;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "reservation_id", updatable = false)
    private UUID reservationId;

    @Column(name = "payload_fingerprint", nullable = false, updatable = false, length = 64)
    private String payloadFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
