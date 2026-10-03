package com.delivery.tracking_service.dto.response;

import com.delivery.tracking.application.api.LocationHistoryPoint;

import java.math.BigDecimal;
import java.time.Instant;

public record LocationHistoryPointResponse(
        Long deliveryId,
        Long shipperId,
        Instant timestamp,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracy,
        BigDecimal speed,
        BigDecimal heading,
        String source) {

    public static LocationHistoryPointResponse from(LocationHistoryPoint point) {
        return new LocationHistoryPointResponse(
                point.deliveryId(), point.shipperId(), point.occurredAt(),
                point.latitude(), point.longitude(), point.accuracy(),
                point.speed(), point.heading(), point.source());
    }
}
