package com.delivery.tracking.application.api;
/** Nullable offline facts and established transport timestamp strings are retained verbatim. */
public record FanoutLocation(Long shipperId, Double latitude, Double longitude, Double accuracy,
        Double speed, Double heading, Boolean isOnline, String lastPing, String updatedAt, Double distance, long occurredAt) {}
