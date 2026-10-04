package com.delivery.tracking.application.api;

import java.util.Optional;
import com.delivery.tracking.domain.PublisherExpiryClaim;

public interface ShipperAvailabilityStorePort {
    Optional<CachedShipperLocation> findCached(Long shipperId);
    void saveOffline(Long shipperId, OfflineShipperLocation location);
    void remove(Long shipperId, java.time.Instant occurredAt);
    /** Atomically checks the lease and claim before changing cache/GEO/online membership. */
    boolean applyOfflineIfExpired(PublisherExpiryClaim claim, Optional<OfflineShipperLocation> cachedOffline, java.time.Instant occurredAt);
}
