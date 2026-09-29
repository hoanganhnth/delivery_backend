package com.delivery.restaurant.application.api;

/** Framework-free input for creating a restaurant serviceability zone. */
public record CreateServiceabilityZoneCommand(
        String name,
        String polygonGeoJson,
        Integer priority,
        Boolean active) {
}
