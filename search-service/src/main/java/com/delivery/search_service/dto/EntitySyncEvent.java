package com.delivery.search_service.dto;

/**
 * Source compatibility facade for tests and older local integrations.
 * Runtime producers/consumers use {@code search-contracts} directly.
 */
@Deprecated(forRemoval = false)
public class EntitySyncEvent extends com.delivery.search.contracts.EntitySyncEvent {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final EntitySyncEvent event = new EntitySyncEvent();
        public Builder eventId(java.util.UUID value) { event.setEventId(value); return this; }
        public Builder occurredAt(java.time.LocalDateTime value) { event.setOccurredAt(value); return this; }
        public Builder entityType(String value) { event.setEntityType(value); return this; }
        public Builder action(String value) { event.setAction(value); return this; }
        public Builder entityId(String value) { event.setEntityId(value); return this; }
        public Builder payload(java.util.Map<String, Object> value) { event.setPayload(value); return this; }
        public EntitySyncEvent build() { return event; }
    }
}
