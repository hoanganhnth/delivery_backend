package com.delivery.tracking_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_history_receipts")
@Getter
@NoArgsConstructor
public class LocationHistoryReceipt {

    public enum Outcome { PERSISTED, SAMPLED_OUT, NO_DELIVERY, OFFLINE_TOMBSTONE }

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "delivery_id")
    private Long deliveryId;

    @Column(name = "shipper_id", nullable = false)
    private Long shipperId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private Outcome outcome;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public LocationHistoryReceipt(UUID eventId, Long deliveryId, Long shipperId,
                                  Instant occurredAt, Outcome outcome) {
        this.eventId = eventId;
        this.deliveryId = deliveryId;
        this.shipperId = shipperId;
        this.occurredAt = occurredAt;
        this.outcome = outcome;
        this.processedAt = Instant.now();
    }
}
