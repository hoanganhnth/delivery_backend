package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.LocationSnapshot;
import java.util.Optional;

/** Persistence/cache boundary for the current tracking projection. */
public interface LocationStorePort {
    void save(LocationSnapshot location);

    Optional<LocationSnapshot> findByShipperId(long shipperId);

    void remove(long shipperId);
}
