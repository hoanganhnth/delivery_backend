package com.delivery.tracking_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Tracking-local principal to shipper mapping for REST and WebSocket publishers. */
@Entity
@Getter
@Setter
@Table(name = "shipper_identity_projection")
public class ShipperIdentityProjection {
    @Id @Column(name = "principal_id") private Long principalId;
    @Column(name = "legacy_user_id", nullable = false, unique = true) private Long legacyUserId;
    @Column(name = "shipper_id", nullable = false, unique = true) private Long shipperId;
    @Column(name = "mapping_version", nullable = false) private Long mappingVersion;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
