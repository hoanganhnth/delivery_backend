package com.delivery.tracking.application;

import com.delivery.tracking.application.api.LocationEventPort;
import com.delivery.tracking.domain.LocationUpdateSource;
import com.delivery.tracking.application.api.LocationStorePort;
import com.delivery.tracking.application.api.TrackingPort;
import com.delivery.tracking.application.api.UpdateLocationCommand;
import com.delivery.tracking.domain.LocationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import com.delivery.tracking.domain.PublisherLease;

/** Framework-free tracking use cases; transport and persistence stay behind ports. */
public final class DefaultTrackingService implements TrackingPort {
    private final LocationStorePort store;
    private final LocationEventPort events;
    private final Clock clock;

    public DefaultTrackingService(LocationStorePort store, LocationEventPort events) {
        this(store, events, Clock.systemUTC());
    }

    /** Creates a framework-free service with an explicit time source. */
    public DefaultTrackingService(LocationStorePort store, LocationEventPort events, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LocationSnapshot updateLocation(UpdateLocationCommand command) {
        Objects.requireNonNull(command, "command");
        var source = Objects.requireNonNull(command.source(), "source");
        if (source != LocationUpdateSource.APPLICATION) throw new IllegalArgumentException("WebSocket updates require publisher lease");
        var snapshot = snapshot(command);
        store.save(snapshot, source);
        replicate(snapshot, source);
        return snapshot;
    }

    @Override
    public Optional<LocationSnapshot> updatePublisherLocation(UpdateLocationCommand command, PublisherLease lease) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(lease, "lease");
        if (command.source() != LocationUpdateSource.WEBSOCKET || command.shipperId() != lease.shipperId()) {
            throw new IllegalArgumentException("WebSocket command and publisher identity must match");
        }
        var snapshot = snapshot(command);
        if (!store.saveIfCurrentPublisher(snapshot, lease)) return Optional.empty();
        replicate(snapshot, LocationUpdateSource.WEBSOCKET);
        return Optional.of(snapshot);
    }

    private LocationSnapshot snapshot(UpdateLocationCommand command) {
        Instant now = Instant.now(clock);
        return new LocationSnapshot(command.shipperId(), command.coordinate(),
                command.accuracy(), command.speed(), command.heading(), command.online(), now, now);
    }

    private void replicate(LocationSnapshot snapshot, LocationUpdateSource source) {
        if (!source.publishesBeforeFanout()) events.broadcast(snapshot, source);
        events.publish(snapshot, source);
        if (source.publishesBeforeFanout()) events.broadcast(snapshot, source);
    }

}
