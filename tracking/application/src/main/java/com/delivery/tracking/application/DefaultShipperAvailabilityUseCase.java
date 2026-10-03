package com.delivery.tracking.application;

import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.PublisherExpiryClaim;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/** Publishes the canonical tombstone after Redis membership has safely changed. */
public final class DefaultShipperAvailabilityUseCase implements ShipperAvailabilityUseCase {
    private final ShipperAvailabilityStorePort store;
    private final ShipperAvailabilityEventPort events;
    private final Clock clock;

    public DefaultShipperAvailabilityUseCase(ShipperAvailabilityStorePort store, ShipperAvailabilityEventPort events) {
        this(store, events, Clock.systemDefaultZone());
    }

    public DefaultShipperAvailabilityUseCase(ShipperAvailabilityStorePort store, ShipperAvailabilityEventPort events, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OfflineShipperLocation markOffline(Long shipperId) {
        return markOffline(shipperId, false);
    }

    @Override
    public OfflineShipperLocation markOfflineAndBroadcast(Long shipperId) {
        return markOffline(shipperId, true);
    }

    @Override
    public boolean markOfflineIfExpired(PublisherExpiryClaim claim) {
        Objects.requireNonNull(claim, "claim");
        var shipperId = Objects.requireNonNull(claim.lease(), "claim.lease").shipperId();
        var cached = store.findCached(shipperId);
        var facts = cached.orElseGet(() -> new CachedShipperLocation(shipperId, null, null, null, null, null, null));
        var offline = new OfflineShipperLocation(facts, LocalDateTime.now(clock));
        if (!store.applyOfflineIfExpired(claim, cached.map(ignored -> offline))) return false;
        events.publish(offline, "OFFLINE_TOMBSTONE");
        events.broadcast(offline);
        return true;
    }

    private OfflineShipperLocation markOffline(Long shipperId, boolean broadcast) {
        if (shipperId == null || shipperId <= 0) throw new IllegalArgumentException("shipperId must be positive");
        var cached = store.findCached(shipperId);
        var timestamp = LocalDateTime.now(clock);
        var facts = cached.orElseGet(() -> new CachedShipperLocation(shipperId, null, null, null, null, null, null));
        var offline = new OfflineShipperLocation(facts, timestamp);
        if (cached.isPresent()) store.saveOffline(shipperId, offline);
        else store.remove(shipperId);
        events.publish(offline, "OFFLINE_TOMBSTONE");
        if (broadcast) events.broadcast(offline);
        return offline;
    }
}
