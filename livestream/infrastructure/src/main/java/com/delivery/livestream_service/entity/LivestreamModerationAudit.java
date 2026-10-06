package com.delivery.livestream_service.entity;

import com.delivery.livestream_service.enums.LivestreamModerationAction;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "livestream_moderation_audits")
public class LivestreamModerationAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "livestream_id", nullable = false, updatable = false)
    private UUID livestreamId;
    @Column(name = "actor_principal_id", nullable = false, updatable = false)
    private Long actorPrincipalId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private LivestreamModerationAction action;
    @Column(nullable = false, length = 1000, updatable = false)
    private String reason;
    @Column(name = "product_id", updatable = false)
    private Long productId;
    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    protected LivestreamModerationAudit() { }

    public LivestreamModerationAudit(UUID livestreamId, Long actorPrincipalId,
            LivestreamModerationAction action, String reason, Long productId) {
        this.livestreamId = livestreamId;
        this.actorPrincipalId = actorPrincipalId;
        this.action = action;
        this.reason = reason;
        this.productId = productId;
    }

    @PrePersist
    void onCreate() {
        appliedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
