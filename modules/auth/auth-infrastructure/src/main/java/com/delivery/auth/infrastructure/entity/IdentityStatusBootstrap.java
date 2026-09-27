package com.delivery.auth_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Receipt that makes a status bootstrap snapshot exactly-once per principal. */
@Entity
@Getter
@Setter
@Table(name = "identity_status_bootstrap")
public class IdentityStatusBootstrap {
    @Id
    @Column(name = "auth_account_id")
    private Long accountId;

    @Column(name = "lifecycle_version", nullable = false)
    private Long lifecycleVersion;

    @Column(name = "emitted_at", nullable = false)
    private LocalDateTime emittedAt;
}
