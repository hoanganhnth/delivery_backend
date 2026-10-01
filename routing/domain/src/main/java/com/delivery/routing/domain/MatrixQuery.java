package com.delivery.routing.domain;

import java.time.Instant;
import java.util.List;

public record MatrixQuery(String profile, Coordinate origin, List<Destination> destinations,
                          Instant departureAt) {
    public MatrixQuery {
        if (origin == null || destinations == null || destinations.isEmpty() || destinations.size() > 25
                || destinations.stream().anyMatch(d -> d == null || d.coordinate() == null)) {
            throw new IllegalArgumentException("Matrix requires one origin and 1-25 destinations");
        }
        destinations = List.copyOf(destinations);
    }

    public record Destination(String id, Coordinate coordinate) { }
}
