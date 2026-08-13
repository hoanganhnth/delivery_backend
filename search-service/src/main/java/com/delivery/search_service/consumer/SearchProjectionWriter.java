package com.delivery.search_service.consumer;

import com.delivery.search_service.dto.EntitySyncEvent;

/** Writes a projection using the event's monotonic source version. */
public interface SearchProjectionWriter {

    void apply(EntitySyncEvent event);
}
