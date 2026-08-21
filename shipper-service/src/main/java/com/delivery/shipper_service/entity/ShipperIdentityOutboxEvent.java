package com.delivery.shipper_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Transactional source for the shipper identity projection. */
@Entity
@Getter
@Setter
@Table(name = "shipper_identity_outbox_events", uniqueConstraints = @UniqueConstraint(
        name = "uk_shipper_identity_outbox_type_aggregate", columnNames = {"event_type", "aggregate_id"}))
public class ShipperIdentityOutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false, unique = true) private UUID eventId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "aggregate_id", nullable = false) private Long aggregateId;
    @Column(nullable = false) private String topic;
    @Column(name = "event_key", nullable = false) private String eventKey;
    @Column(nullable = false, columnDefinition = "TEXT") private String payload;
    @Column(nullable = false) private int attempts;
    @Column(name = "available_at", nullable = false) private LocalDateTime availableAt;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now; updatedAt = now;
        if (availableAt == null) availableAt = now;
    }
    @jakarta.persistence.PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
