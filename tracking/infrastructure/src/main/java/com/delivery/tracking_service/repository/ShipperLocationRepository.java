package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
public interface ShipperLocationRepository {
    void cacheShipperLocation(Long shipperId, ShipperLocationResponse location);
    ShipperLocationResponse getCachedShipperLocation(Long shipperId);
    void removeShipperLocationCache(Long shipperId);
}
