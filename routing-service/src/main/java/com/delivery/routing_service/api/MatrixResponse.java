package com.delivery.routing_service.api;

import java.time.Instant;
import java.util.List;

public record MatrixResponse(
        List<Result> results,
        Instant generatedAt) {

    public record Result(
            String id,
            long durationSeconds,
            long distanceMeters,
            String source) {
    }
}
