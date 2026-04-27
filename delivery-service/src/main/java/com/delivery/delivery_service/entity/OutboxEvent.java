package com.delivery.delivery_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ✅ Outbox Event Entity — Transactional Outbox Pattern
 *
 * Lưu event cùng DB transaction với business logic.
 * MessageRelay sẽ poll bảng này → gửi lên Kafka → đánh dấu SENT.
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status, createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "event_key")
    private String eventKey;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime sentAt;

    @Builder.Default
    private int retryCount = 0;

    private String errorMessage;

    public enum OutboxStatus {
        PENDING,
        SENT,
        FAILED
    }
}
