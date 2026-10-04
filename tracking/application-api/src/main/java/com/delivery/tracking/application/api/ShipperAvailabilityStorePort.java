package com.delivery.tracking.application.api;

import java.util.Optional;
import com.delivery.tracking.domain.PublisherExpiryClaim;

public interface ShipperAvailabilityStorePort {
    Optional<CachedShipperLocation> findCached(Long shipperId);
    void saveOffline(Long shipperId, OfflineShipperLocation location);
    void remove(Long shipperId, java.time.Instant occurredAt);
    /** Atomically checks lease/claim admission and latest projection; admitted old/equal facts are no-ops. */
    boolean applyOfflineIfExpired(PublisherExpiryClaim claim, Optional<OfflineShipperLocation> cachedOffline, java.time.Instant occurredAt);
}
