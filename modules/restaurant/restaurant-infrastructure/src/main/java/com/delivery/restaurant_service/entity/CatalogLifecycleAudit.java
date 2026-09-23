package com.delivery.restaurant_service.entity;
// Package retained during migration to preserve JPA scanning and existing callers.

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "catalog_lifecycle_audits")
@Getter
@Setter
@NoArgsConstructor
public class CatalogLifecycleAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 32)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;
    @Column(nullable = false, updatable = false, length = 32)
    private String action;
    @Column(name = "actor_principal_id", nullable = false, updatable = false)
    private Long actorPrincipalId;
    @Column(name = "actor_role", nullable = false, updatable = false, length = 32)
    private String actorRole;
    @Column(name = "before_status", nullable = false, updatable = false, length = 32)
    private String beforeStatus;
    @Column(name = "after_status", nullable = false, updatable = false, length = 32)
    private String afterStatus;
    @Column(name = "before_version", nullable = false, updatable = false)
    private Long beforeVersion;
    @Column(name = "after_version", nullable = false, updatable = false)
    private Long afterVersion;
    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
    private String correlationId;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}
