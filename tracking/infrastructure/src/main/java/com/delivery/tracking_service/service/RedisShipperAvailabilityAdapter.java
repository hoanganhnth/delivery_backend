package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.PublisherExpiryClaim;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperLocationRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Redis/Kafka/fanout representation mapping; the core owns offline decisions and ordering. */
@Component
public class RedisShipperAvailabilityAdapter implements ShipperAvailabilityStorePort, ShipperAvailabilityEventPort {
    private final ShipperLocationRepository locations;
    private final ShipperLocationEventPublisher events;
    private final LocationFanoutPublisher fanout;

    public RedisShipperAvailabilityAdapter(ShipperLocationRepository locations, ShipperLocationEventPublisher events,
            LocationFanoutPublisher fanout) {
        this.locations = locations; this.events = events; this.fanout = fanout;
    }
    @Override public Optional<CachedShipperLocation> findCached(Long shipperId) {
        return Optional.ofNullable(locations.getCachedShipperLocation(shipperId)).map(row ->
                new CachedShipperLocation(row.getShipperId(), row.getLatitude(), row.getLongitude(),
                        row.getAccuracy(), row.getSpeed(), row.getHeading(), row.getDistance()));
    }
    @Override public void saveOffline(Long shipperId, OfflineShipperLocation location) {
        locations.cacheShipperLocation(shipperId, toResponse(location), location.timestamp().toEpochMilli());
    }
    @Override public void remove(Long shipperId, java.time.Instant occurredAt) { locations.removeShipperLocationCache(shipperId, occurredAt.toEpochMilli()); }
    @Override public boolean applyOfflineIfExpired(PublisherExpiryClaim claim, Optional<OfflineShipperLocation> cachedOffline, java.time.Instant occurredAt) {
        return locations.applyOfflineIfExpired(claim, cachedOffline.map(RedisShipperAvailabilityAdapter::toResponse), occurredAt.toEpochMilli());
    }
    @Override public void publish(OfflineShipperLocation location, String source) {
        events.publishLocationUpdate(toResponse(location), source, location.timestamp().toEpochMilli());
    }
    @Override public void broadcast(OfflineShipperLocation location) {
        fanout.publish(toResponse(location), location.timestamp().toEpochMilli());
    }
    public static ShipperLocationResponse toResponse(OfflineShipperLocation location) {
        var facts = location.facts(); var response = new ShipperLocationResponse();
        response.setShipperId(facts.shipperId()); response.setLatitude(facts.latitude()); response.setLongitude(facts.longitude());
        response.setAccuracy(facts.accuracy()); response.setSpeed(facts.speed()); response.setHeading(facts.heading()); response.setDistance(facts.distance());
        var timestamp = java.time.LocalDateTime.ofInstant(location.timestamp(), java.time.ZoneId.systemDefault()).toString();
        response.setIsOnline(false); response.setLastPing(timestamp); response.setUpdatedAt(timestamp);
        return response;
    }
}
