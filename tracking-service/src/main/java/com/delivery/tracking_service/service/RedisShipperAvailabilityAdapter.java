package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.*;
import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperLocationRepository;
import com.delivery.tracking_service.websocket.ShipperLocationWebSocketHandler;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Redis/Kafka/fanout representation mapping; the core owns offline decisions and ordering. */
@Component
public class RedisShipperAvailabilityAdapter implements ShipperAvailabilityStorePort, ShipperAvailabilityEventPort {
    private final ShipperLocationRepository locations;
    private final ShipperLocationEventPublisher events;
    private final ObjectProvider<ShipperLocationWebSocketHandler> sockets;

    public RedisShipperAvailabilityAdapter(ShipperLocationRepository locations, ShipperLocationEventPublisher events,
            ObjectProvider<ShipperLocationWebSocketHandler> sockets) {
        this.locations = locations; this.events = events; this.sockets = sockets;
    }
    @Override public Optional<CachedShipperLocation> findCached(Long shipperId) {
        return Optional.ofNullable(locations.getCachedShipperLocation(shipperId)).map(row ->
                new CachedShipperLocation(row.getShipperId(), row.getLatitude(), row.getLongitude(),
                        row.getAccuracy(), row.getSpeed(), row.getHeading(), row.getDistance()));
    }
    @Override public void saveOffline(Long shipperId, OfflineShipperLocation location) {
        locations.cacheShipperLocation(shipperId, toResponse(location));
    }
    @Override public void remove(Long shipperId) { locations.removeShipperLocationCache(shipperId); }
    @Override public void publish(OfflineShipperLocation location, String source) {
        events.publishLocationUpdate(toResponse(location), source);
    }
    @Override public void broadcast(OfflineShipperLocation location) {
        sockets.getObject().broadcastShipperLocation(toResponse(location));
    }
    public static ShipperLocationResponse toResponse(OfflineShipperLocation location) {
        var facts = location.facts(); var response = new ShipperLocationResponse();
        response.setShipperId(facts.shipperId()); response.setLatitude(facts.latitude()); response.setLongitude(facts.longitude());
        response.setAccuracy(facts.accuracy()); response.setSpeed(facts.speed()); response.setHeading(facts.heading()); response.setDistance(facts.distance());
        response.setIsOnline(false); response.setLastPing(location.timestamp().toString()); response.setUpdatedAt(location.timestamp().toString());
        return response;
    }
}
