package com.delivery.tracking_service.dto.response;

import com.delivery.tracking_service.entity.ShipperLocationHistory;

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

    public static LocationHistoryPointResponse from(ShipperLocationHistory point) {
        return new LocationHistoryPointResponse(
                point.getDeliveryId(), point.getShipperId(), point.getOccurredAt(),
                point.getLatitude(), point.getLongitude(), point.getAccuracy(),
                point.getSpeed(), point.getHeading(), point.getSource());
    }
}
