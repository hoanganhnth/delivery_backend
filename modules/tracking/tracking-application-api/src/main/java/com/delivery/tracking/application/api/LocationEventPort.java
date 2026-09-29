package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.LocationSnapshot;

/** Event boundary for propagating tracking changes to other bounded contexts. */
public interface LocationEventPort {
    void publish(LocationSnapshot location, String source);
}
