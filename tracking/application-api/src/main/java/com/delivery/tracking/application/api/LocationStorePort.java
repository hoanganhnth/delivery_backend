package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.LocationSnapshot;
import com.delivery.tracking.domain.LocationUpdateSource;
import com.delivery.tracking.domain.PublisherLease;

/** Persistence/cache boundary for the current tracking projection. */
public interface LocationStorePort {
    /** Applies the latest occurrence projection; older/equal facts leave it unchanged. */
    void save(LocationSnapshot location, LocationUpdateSource source);
    /** False rejects a stale lease; admitted old/equal facts may be projection no-ops. */
    boolean saveIfCurrentPublisher(LocationSnapshot location, PublisherLease lease);
}
