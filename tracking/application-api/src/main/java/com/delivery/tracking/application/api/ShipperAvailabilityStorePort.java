package com.delivery.tracking.application.api;

import java.util.Optional;

public interface ShipperAvailabilityStorePort {
    Optional<CachedShipperLocation> findCached(Long shipperId);
    void saveOffline(Long shipperId, OfflineShipperLocation location);
    void remove(Long shipperId);
}
