package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking.domain.PublisherExpiryClaim;
import java.util.Optional;
public interface ShipperLocationRepository {
    void cacheShipperLocation(Long shipperId, ShipperLocationResponse location, long occurredAt);
    boolean cacheIfCurrentPublisher(com.delivery.tracking.domain.PublisherLease lease, ShipperLocationResponse location, long occurredAt);
    ShipperLocationResponse getCachedShipperLocation(Long shipperId);
    void removeShipperLocationCache(Long shipperId, long occurredAt);
    boolean applyOfflineIfExpired(PublisherExpiryClaim claim, Optional<ShipperLocationResponse> cachedOffline, long occurredAt);
}
