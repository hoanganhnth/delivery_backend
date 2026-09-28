package com.delivery.shipper.infrastructure.adapter;

import com.delivery.shipper.application.api.ShipperPorts;
import com.delivery.shipper.infrastructure.client.TrackingAvailabilityClient;
import org.springframework.stereotype.Component;

@Component
public final class TrackingAvailabilityAdapter implements ShipperPorts.TrackingAvailability {
    private final TrackingAvailabilityClient client;
    public TrackingAvailabilityAdapter(TrackingAvailabilityClient client) { this.client = client; }
    @Override public void markOffline(long shipperId, long requestedAtEpochMillis) { client.markOffline(shipperId); }
}
