package com.delivery.auth_service.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "identity_outbox_events")
public class IdentityOutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false, unique = true) private UUID eventId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "aggregate_id", nullable = false) private Long aggregateId;
    @Column(nullable = false) private String topic;
    @Column(name = "event_key", nullable = false) private String eventKey;
    @Column(columnDefinition = "TEXT", nullable = false) private String payload;
    @Column(nullable = false) private int attempts;
    @Column(name = "available_at", nullable = false) private LocalDateTime availableAt;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; if (availableAt == null) availableAt = now; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
