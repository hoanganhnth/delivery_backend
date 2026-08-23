package com.delivery.routing_service.api;

public record RouteResponse(
        long durationSeconds,
        long distanceMeters,
        String geometry,
        String source) {
}
