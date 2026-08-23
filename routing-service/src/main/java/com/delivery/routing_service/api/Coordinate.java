package com.delivery.routing_service.api;

public record Coordinate(double lat, double lng) {

    public Coordinate {
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Invalid coordinate");
        }
    }
}
