package com.delivery.restaurant_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "restaurant_outbox_events", indexes = {
        @Index(name = "idx_restaurant_outbox_pending", columnList = "status,next_attempt_at,created_at"),
        @Index(name = "idx_restaurant_outbox_order", columnList = "aggregate_id,created_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_restaurant_outbox_event_id", columnNames = "event_id"))
@Getter
@Setter
@NoArgsConstructor
public class RestaurantOutboxEvent {
    public enum Status { PENDING, SENT, DEAD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private String eventType;
    @Column(nullable = false, updatable = false)
    private String topic;
    @Column(name = "event_key", nullable = false, updatable = false)
    private String eventKey;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(name = "traceparent", length = 55, updatable = false)
    private String traceparent;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    @Column(name = "last_error", length = 2000)
    private String lastError;
}
