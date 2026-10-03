package com.delivery.tracking.application.api;

public interface ShipperAvailabilityEventPort {
    void publish(OfflineShipperLocation location, String source);
    void broadcast(OfflineShipperLocation location);
}
