package com.delivery.flashsale_service.entity;

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

/**
 * Durable Order-event identity for the flash-sale reservation boundary. The
 * receipt is committed in the same transaction as its stock transition.
 */
@Entity
@Table(name = "flash_sale_order_reservation_receipts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleOrderReservationReceipt {

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
