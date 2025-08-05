package com.delivery.notification_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * ✅ Notification Entity theo Backend Instructions
 */
@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "type", nullable = false)
    private String type; // ORDER_CREATED, ORDER_DELIVERED, etc.

    @Column(name = "priority")
    private String priority = "MEDIUM"; // HIGH, MEDIUM, LOW

    @Column(name = "status")
    private String status = "PENDING"; // PENDING, SENT, DELIVERED, FAILED, READ

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "related_entity_id")
    private Long relatedEntityId; // orderId, deliveryId, etc.

    @Column(name = "related_entity_type")
    private String relatedEntityType; // ORDER, DELIVERY, SHIPPER

    @Column(name = "data", columnDefinition = "TEXT")
    private String data; // JSON data for additional info

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "creator_id")
    private Long creatorId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
