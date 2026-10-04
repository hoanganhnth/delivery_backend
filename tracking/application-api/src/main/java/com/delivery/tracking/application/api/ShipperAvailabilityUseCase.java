package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.PublisherExpiryClaim;

public interface ShipperAvailabilityUseCase {
    OfflineShipperLocation markOffline(Long shipperId);
    OfflineShipperLocation markOfflineAndBroadcast(Long shipperId);
    /** Returns false when reconnect or a newer recovery claim fences this attempt. */
    boolean markOfflineIfExpired(PublisherExpiryClaim claim);
}
