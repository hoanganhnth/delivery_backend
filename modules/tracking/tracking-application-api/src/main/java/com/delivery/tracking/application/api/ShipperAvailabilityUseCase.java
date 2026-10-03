package com.delivery.tracking.application.api;

public interface ShipperAvailabilityUseCase {
    OfflineShipperLocation markOffline(Long shipperId);
    OfflineShipperLocation markOfflineAndBroadcast(Long shipperId);
}
