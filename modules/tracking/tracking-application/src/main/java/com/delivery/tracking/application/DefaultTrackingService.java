package com.delivery.tracking.application;

import com.delivery.tracking.application.api.LocationEventPort;
import com.delivery.tracking.application.api.LocationStorePort;
import com.delivery.tracking.application.api.TrackingPort;
import com.delivery.tracking.application.api.UpdateLocationCommand;
import com.delivery.tracking.domain.LocationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Framework-free tracking use cases; transport and persistence stay behind ports. */
public final class DefaultTrackingService implements TrackingPort {
    private static final String UPDATE_SOURCE = "APPLICATION";

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
        Instant now = Instant.now(clock);
        LocationSnapshot snapshot = new LocationSnapshot(command.shipperId(), command.coordinate(),
                command.accuracy(), command.speed(), command.heading(), command.online(), now, now);
        store.save(snapshot);
        events.publish(snapshot, UPDATE_SOURCE);
        return snapshot;
    }

}
