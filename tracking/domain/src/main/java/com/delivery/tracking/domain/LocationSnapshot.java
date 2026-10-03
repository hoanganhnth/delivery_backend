package com.delivery.tracking.domain;

import java.time.Instant;

/** Immutable tracking state; transport and persistence representations stay outside the domain. */
public record LocationSnapshot(
        long shipperId,
        Coordinate coordinate,
        Double accuracy,
        Double speed,
        Double heading,
        boolean online,
        Instant lastPing,
        Instant updatedAt) {

    public LocationSnapshot {
        if (shipperId <= 0 || coordinate == null || lastPing == null || updatedAt == null) {
            throw new IllegalArgumentException("shipper, coordinate and timestamps are required");
        }
        requireFiniteIfPresent(accuracy, "accuracy");
        requireFiniteIfPresent(speed, "speed");
        requireFiniteIfPresent(heading, "heading");
    }

    private static void requireFiniteIfPresent(Double value, String field) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}
