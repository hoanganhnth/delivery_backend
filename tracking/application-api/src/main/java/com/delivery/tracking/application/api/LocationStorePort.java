package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.LocationSnapshot;
import com.delivery.tracking.domain.LocationUpdateSource;
import com.delivery.tracking.domain.PublisherLease;

/** Persistence/cache boundary for the current tracking projection. */
public interface LocationStorePort {
    void save(LocationSnapshot location, LocationUpdateSource source);
    boolean saveIfCurrentPublisher(LocationSnapshot location, PublisherLease lease);
}
