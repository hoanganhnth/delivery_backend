package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.LocationSnapshot;
import com.delivery.tracking.domain.LocationUpdateSource;
import com.delivery.tracking_service.repository.ShipperLocationRepository;
import org.springframework.stereotype.Component;

/** Technical Redis/JSON/Kafka/PubSub mapping; application owns source and ordering. */
@Component
public class RedisLocationUpdateAdapter implements LocationStorePort, LocationEventPort {
    private final ShipperLocationRepository locations;
    private final ShipperLocationEventPublisher events;
    private final LocationFanoutPublisher fanout;
    public RedisLocationUpdateAdapter(ShipperLocationRepository locations, ShipperLocationEventPublisher events, LocationFanoutPublisher fanout) {
        this.locations = locations; this.events = events; this.fanout = fanout;
    }
    @Override public void save(LocationSnapshot location, LocationUpdateSource source) {
        locations.cacheShipperLocation(location.shipperId(), LocationUpdateMapper.toResponse(location, source));
    }
    @Override public boolean saveIfCurrentPublisher(LocationSnapshot location, com.delivery.tracking.domain.PublisherLease lease) {
        return locations.cacheIfCurrentPublisher(lease, LocationUpdateMapper.toResponse(location, LocationUpdateSource.WEBSOCKET));
    }
    @Override public void publish(LocationSnapshot location, LocationUpdateSource source) {
        events.publishLocationUpdate(LocationUpdateMapper.toResponse(location, source), source.name(), location.updatedAt().toEpochMilli());
    }
    @Override public void broadcast(LocationSnapshot location, LocationUpdateSource source) {
        fanout.publish(LocationUpdateMapper.toResponse(location, source));
    }
}
