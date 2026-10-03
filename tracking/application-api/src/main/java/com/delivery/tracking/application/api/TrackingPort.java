package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.LocationSnapshot;
import com.delivery.tracking.domain.PublisherLease;
import java.util.Optional;

/** Application boundary for tracking commands; adapters own HTTP, Redis and WebSocket details. */
public interface TrackingPort {
    LocationSnapshot updateLocation(UpdateLocationCommand command);
    Optional<LocationSnapshot> updatePublisherLocation(UpdateLocationCommand command, PublisherLease lease);

}
