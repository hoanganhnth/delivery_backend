package com.delivery.routing_service.api;

import java.time.Instant;

public record RouteRequest(
        String profile,
        Coordinate origin,
        Coordinate destination,
        Instant departureAt,
        boolean includeGeometry) {
}
