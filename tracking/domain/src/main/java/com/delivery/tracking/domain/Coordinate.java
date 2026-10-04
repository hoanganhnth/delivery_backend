package com.delivery.tracking.domain;

/** A validated geographic point used by tracking adapters and use cases. */
public record Coordinate(double latitude, double longitude) {
    public Coordinate {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid coordinate");
        }
    }
}
