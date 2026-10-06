package com.delivery.search.application.api;

import com.delivery.search.domain.EntitySyncRules;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Transport-neutral event values; payload stays opaque until the storage adapter. */
public record ProjectionInput(UUID eventId, LocalDateTime occurredAt, String entityType,
        String action, String entityId, Map<String, Object> payload, Long aggregateVersion,
        LocalDateTime deletedAt, String deletionReason) {
    public EntitySyncRules.Metadata metadata() {
        return new EntitySyncRules.Metadata(eventId, occurredAt, entityType, action, entityId,
                payload != null, aggregateVersion, deletedAt);
    }
}
