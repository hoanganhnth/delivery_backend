package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking.domain.PublisherExpiryClaim;
import java.util.Optional;
public interface ShipperLocationRepository {
    /** Applies the latest projection; old/equal-time facts are no-ops, not persistence failures. */
    void cacheShipperLocation(Long shipperId, ShipperLocationResponse location, long occurredAt);
    /** Admission is false only for a stale publisher; an admitted old/equal fact leaves projection intact. */
    boolean cacheIfCurrentPublisher(com.delivery.tracking.domain.PublisherLease lease, ShipperLocationResponse location, long occurredAt);
    ShipperLocationResponse getCachedShipperLocation(Long shipperId);
    void removeShipperLocationCache(Long shipperId, long occurredAt);
    /** Claim admission is separate from old/equal-time projection no-op. */
    boolean applyOfflineIfExpired(PublisherExpiryClaim claim, Optional<ShipperLocationResponse> cachedOffline, long occurredAt);
}
