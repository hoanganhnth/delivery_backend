package com.delivery.search.contracts;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Stable wire envelope for the existing entity-sync topic. */
public class EntitySyncEvent {
    private UUID eventId;
    private LocalDateTime occurredAt;
    private String entityType;
    private String action;
    private String entityId;
    private Map<String, Object> payload;
    private Long aggregateVersion;
    private LocalDateTime deletedAt;
    private String deletionReason;

    public EntitySyncEvent() {
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public Long getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(Long aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public String getDeletionReason() { return deletionReason; }
    public void setDeletionReason(String deletionReason) { this.deletionReason = deletionReason; }

}
