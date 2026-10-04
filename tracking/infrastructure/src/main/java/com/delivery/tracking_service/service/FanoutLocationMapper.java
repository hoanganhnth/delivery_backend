package com.delivery.tracking_service.service;
import com.delivery.tracking.application.api.FanoutLocation;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
public final class FanoutLocationMapper {
    private FanoutLocationMapper() {}
    public static FanoutLocation from(ShipperLocationResponse location, long occurredAt) {
        if (location == null) return null;
        return new FanoutLocation(location.getShipperId(), location.getLatitude(), location.getLongitude(),
                location.getAccuracy(), location.getSpeed(), location.getHeading(), location.getIsOnline(),
                location.getLastPing(), location.getUpdatedAt(), location.getDistance(), occurredAt);
    }
    public static ShipperLocationResponse toResponse(FanoutLocation location) {
        var dto = new ShipperLocationResponse(); dto.setShipperId(location.shipperId());
        dto.setLatitude(location.latitude()); dto.setLongitude(location.longitude()); dto.setAccuracy(location.accuracy());
        dto.setSpeed(location.speed()); dto.setHeading(location.heading()); dto.setIsOnline(location.isOnline());
        dto.setLastPing(location.lastPing()); dto.setUpdatedAt(location.updatedAt()); dto.setDistance(location.distance());
        return dto;
    }
}
