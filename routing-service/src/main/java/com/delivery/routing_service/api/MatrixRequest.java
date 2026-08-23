package com.delivery.routing_service.api;

import java.time.Instant;
import java.util.List;

public record MatrixRequest(
        String profile,
        Coordinate origin,
        List<Destination> destinations,
        Instant departureAt) {

    public record Destination(String id, Coordinate coordinate) {
    }
}
