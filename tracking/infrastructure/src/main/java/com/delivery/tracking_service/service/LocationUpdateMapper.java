package com.delivery.tracking_service.service;

import com.delivery.tracking.domain.LocationUpdateSource;
import com.delivery.tracking.domain.LocationSnapshot;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Retains established Instant REST and local-date-time WebSocket encodings. */
public final class LocationUpdateMapper {
    private LocationUpdateMapper() {}
    public static ShipperLocationResponse toResponse(LocationSnapshot location, LocationUpdateSource source) {
        var response = new ShipperLocationResponse(); response.setShipperId(location.shipperId());
        response.setLatitude(location.coordinate().latitude()); response.setLongitude(location.coordinate().longitude());
        response.setAccuracy(location.accuracy()); response.setSpeed(location.speed()); response.setHeading(location.heading());
        response.setIsOnline(location.online());
        response.setLastPing(timestamp(location.lastPing(), source)); response.setUpdatedAt(timestamp(location.updatedAt(), source));
        return response;
    }
    private static String timestamp(java.time.Instant time, LocationUpdateSource source) {
        return source == LocationUpdateSource.WEBSOCKET
                ? LocalDateTime.ofInstant(time, ZoneId.systemDefault()).toString() : time.toString();
    }
}
