package com.delivery.shipper_service.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "identity_inbox_receipts")
public class IdentityInboxReceipt {
    @Id private UUID eventId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "principal_id", nullable = false) private Long principalId;
    @Column(name = "payload_fingerprint", nullable = false) private String payloadFingerprint;
    @Column(name = "processed_at", nullable = false) private LocalDateTime processedAt;
}
