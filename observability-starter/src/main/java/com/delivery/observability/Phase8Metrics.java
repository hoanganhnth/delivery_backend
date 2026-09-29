package com.delivery.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/** Stable low-cardinality counters shared by Phase 8 high-change services. */
public final class Phase8Metrics {
    private final Counter tombstoneEmitted;
    private final Counter tombstoneApplied;
    private final Counter staleEventRejected;
    private final Counter reservationConflict;
    private final Counter softDeleteFailure;

    public Phase8Metrics(MeterRegistry registry, String service) {
        Objects.requireNonNull(registry, "registry");
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("service must not be blank");
        }
        tombstoneEmitted = counter(registry, "delivery.tombstones", service, "emitted");
        tombstoneApplied = counter(registry, "delivery.tombstones", service, "applied");
        staleEventRejected = counter(registry, "delivery.events", service, "stale_rejected");
        reservationConflict = counter(registry, "delivery.reservations", service, "conflict");
        softDeleteFailure = counter(registry, "delivery.soft_deletes", service, "failure");
    }

    public void tombstoneEmitted() { tombstoneEmitted.increment(); }
    public void tombstoneApplied() { tombstoneApplied.increment(); }
    public void staleEventRejected() { staleEventRejected.increment(); }
    public void reservationConflict() { reservationConflict.increment(); }
    public void softDeleteFailure() { softDeleteFailure.increment(); }

    private static Counter counter(MeterRegistry registry, String name, String service, String outcome) {
        String tag = name.equals("delivery.tombstones") ? "action" : "outcome";
        return Counter.builder(name).tag("service", service).tag(tag, outcome).register(registry);
    }
}
