package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.LocationSnapshot;
import com.delivery.tracking.domain.LocationUpdateSource;

/** Event boundary for propagating tracking changes to other bounded contexts. */
public interface LocationEventPort {
    void publish(LocationSnapshot location, LocationUpdateSource source);
    void broadcast(LocationSnapshot location, LocationUpdateSource source);
}
