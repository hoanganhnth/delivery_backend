package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import org.springframework.data.geo.Point;

import java.util.List;

public interface ShipperLocationRepository {
    void cacheShipperLocation(Long shipperId, ShipperLocationResponse location);
    ShipperLocationResponse getCachedShipperLocation(Long shipperId);
    List<ShipperLocationResponse> findShippersWithinRadius(Double centerLat, Double centerLng, Double radiusKm, Integer limit);
    Double getDistanceBetweenShippers(Long shipperId1, Long shipperId2);
    Point getShipperGeoPosition(Long shipperId);
    List<ShipperLocationResponse> getAllOnlineShippers();
    void removeShipperLocationCache(Long shipperId);
    boolean isRedisAvailable();
    int getActiveConnections();
    int getTotalConnections();
    int getCachedShippersCount();
}
