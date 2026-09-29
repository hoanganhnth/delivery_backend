package com.delivery.tracking_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipper_location_history", uniqueConstraints =
        @UniqueConstraint(name = "uk_location_history_event", columnNames = "event_id"))
@Getter
@NoArgsConstructor
public class ShipperLocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private Long deliveryId;

    @Column(name = "shipper_id", nullable = false, updatable = false)
    private Long shipperId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "latitude", nullable = false, precision = 8, scale = 5)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 8, scale = 5)
    private BigDecimal longitude;

    @Column(name = "accuracy", precision = 8, scale = 2)
    private BigDecimal accuracy;

    @Column(name = "speed", precision = 8, scale = 2)
    private BigDecimal speed;

    @Column(name = "heading", precision = 8, scale = 2)
    private BigDecimal heading;

    @Column(name = "source", nullable = false, updatable = false, length = 32)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ShipperLocationHistory(UUID eventId, Long deliveryId, Long shipperId,
                                  Instant occurredAt, BigDecimal latitude,
                                  BigDecimal longitude, BigDecimal accuracy,
                                  BigDecimal speed, BigDecimal heading, String source) {
        this.eventId = eventId;
        this.deliveryId = deliveryId;
        this.shipperId = shipperId;
        this.occurredAt = occurredAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.speed = speed;
        this.heading = heading;
        this.source = source;
        this.createdAt = Instant.now();
    }
}
