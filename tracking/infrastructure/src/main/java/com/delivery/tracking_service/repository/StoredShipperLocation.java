package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;

/** One Redis value pairs nullable public details with absolute ordering metadata. */
public record StoredShipperLocation(ShipperLocationResponse location, long occurredAt) {
    public StoredShipperLocation {
        if (occurredAt <= 0) throw new IllegalArgumentException("positive location occurrence time is required");
    }
}
