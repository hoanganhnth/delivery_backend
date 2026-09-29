package com.delivery.routing.domain;

public record RouteResult(long durationSeconds, long distanceMeters, String geometry, String source) {
    public RouteResult {
        if (durationSeconds < 0 || distanceMeters < 0 || source == null || source.isBlank()) {
            throw new IllegalArgumentException("Route result duration, distance and source are required");
        }
    }
}
