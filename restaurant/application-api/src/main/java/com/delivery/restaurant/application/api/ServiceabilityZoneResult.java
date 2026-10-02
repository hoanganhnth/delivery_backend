package com.delivery.restaurant.application.api;

import java.time.LocalDateTime;

/** Framework-free serviceability-zone projection returned to the host adapter. */
public record ServiceabilityZoneResult(
        Long id,
        Long restaurantId,
        String name,
        String polygonGeoJson,
        Integer priority,
        boolean active,
        Long revision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
