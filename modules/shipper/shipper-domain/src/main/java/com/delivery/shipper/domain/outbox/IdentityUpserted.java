package com.delivery.shipper.domain.outbox;

import java.time.Instant;
import java.util.UUID;

public record IdentityUpserted(UUID eventId, long principalId, Long legacyUserId, long shipperId, long mappingVersion, Instant occurredAt) {
    public IdentityUpserted {
        if (eventId == null || occurredAt == null || principalId <= 0 || shipperId <= 0 || mappingVersion < 1) throw new IllegalArgumentException("invalid identity event");
        if (legacyUserId != null && legacyUserId <= 0) throw new IllegalArgumentException("legacyUserId must be positive");
    }
}
