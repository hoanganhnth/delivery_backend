package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.LocationSnapshot;

/** Application boundary for tracking commands; adapters own HTTP, Redis and WebSocket details. */
public interface TrackingPort {
    LocationSnapshot updateLocation(UpdateLocationCommand command);

}
