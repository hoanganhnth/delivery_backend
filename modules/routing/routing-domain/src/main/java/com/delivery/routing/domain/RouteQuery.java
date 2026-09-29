package com.delivery.routing.domain;

import java.time.Instant;

public record RouteQuery(String profile, Coordinate origin, Coordinate destination,
                         Instant departureAt, boolean includeGeometry) {
    public RouteQuery {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("Route origin and destination are required");
        }
    }
}
