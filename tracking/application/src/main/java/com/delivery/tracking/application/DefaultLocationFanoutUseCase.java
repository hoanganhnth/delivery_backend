package com.delivery.tracking.application;
import com.delivery.tracking.application.api.FanoutLocation;
import com.delivery.tracking.application.api.FanoutDeliveryReadPort;
import com.delivery.tracking.application.api.LocationFanoutEventPort;
import com.delivery.tracking.application.api.LocationFanoutUseCase;
import java.util.Objects;
import java.util.Set;
/** Selects exact assigned rooms; best-effort fanout preserves Redis-backed recovery. */
public final class DefaultLocationFanoutUseCase implements LocationFanoutUseCase {
    private final FanoutDeliveryReadPort assignments;
    private final LocationFanoutEventPort events;
    public DefaultLocationFanoutUseCase(FanoutDeliveryReadPort assignments, LocationFanoutEventPort events) {
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.events = Objects.requireNonNull(events, "events");
    }
    @Override public void publish(FanoutLocation location) {
        if (location == null || location.shipperId() == null) return;
        Set<Long> active = assignments.activeDeliveries(location.shipperId());
        if (active == null || active.isEmpty()) {
            active = assignments.activeDelivery(location.shipperId()).map(Set::of).orElseGet(Set::of);
        }
        for (Long deliveryId : active) {
            try { events.publish(deliveryId, location); }
            catch (Exception failure) { events.failed(location.shipperId(), failure); }
        }
    }
}
