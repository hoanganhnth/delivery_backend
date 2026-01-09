package com.delivery.livestream_service.entity;

import com.delivery.livestream_service.enums.LivestreamEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "livestream_events")
public class LivestreamEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "livestream_id", nullable = false)
    private UUID livestreamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private LivestreamEventType type;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
