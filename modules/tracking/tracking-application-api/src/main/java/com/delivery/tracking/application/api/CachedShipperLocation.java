package com.delivery.tracking.application.api;

/** Cached canonical facts; partial coordinates are retained for an offline tombstone. */
public record CachedShipperLocation(Long shipperId, Double latitude, Double longitude,
        Double accuracy, Double speed, Double heading, Double distance) {}
