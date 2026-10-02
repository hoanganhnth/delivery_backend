package com.delivery.restaurant.application.api;

/** Framework-free input for updating a restaurant serviceability zone. */
public record UpdateServiceabilityZoneCommand(
        Long revision,
        String name,
        String polygonGeoJson,
        Integer priority,
        Boolean active) {
}
