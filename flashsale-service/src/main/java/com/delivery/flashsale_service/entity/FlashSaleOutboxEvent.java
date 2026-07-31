package com.delivery.flashsale_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "flash_sale_outbox_events", indexes = {
        @Index(name = "idx_flash_outbox_pending", columnList = "status,next_attempt_at,created_at,event_id"),
        @Index(name = "idx_flash_outbox_aggregate", columnList = "aggregate_type,aggregate_id,created_at")
})
@Getter @Setter @NoArgsConstructor
public class FlashSaleOutboxEvent {
    public enum Status { PENDING, SENT, DEAD }
    @Id @Column(name = "event_id", nullable = false, updatable = false) private UUID eventId;
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 64) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 64) private String aggregateId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 128) private String eventType;
    @Column(nullable = false, updatable = false) private String topic;
    @Column(name = "event_key", nullable = false, updatable = false, length = 128) private String eventKey;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT") private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private LocalDateTime nextAttemptAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "sent_at") private LocalDateTime sentAt;
    @Column(name = "last_error", length = 2000) private String lastError;
}
