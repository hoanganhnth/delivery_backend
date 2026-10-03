package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.LocationSnapshot;

/** Persistence/cache boundary for the current tracking projection. */
public interface LocationStorePort {
    void save(LocationSnapshot location);
}
