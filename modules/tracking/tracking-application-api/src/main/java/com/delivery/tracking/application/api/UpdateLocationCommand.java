package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.Coordinate;
import com.delivery.tracking.domain.LocationUpdateSource;

/** Trusted application input after transport validation and identity resolution. */
public record UpdateLocationCommand(
        long shipperId,
        Coordinate coordinate,
        Double accuracy,
        Double speed,
        Double heading,
        boolean online,
        LocationUpdateSource source) {}
