package com.delivery.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Phase8MetricsTest {
    @Test
    void recordsStableCountersWithServiceAndOutcomeTags() {
        var registry = new SimpleMeterRegistry();
        var metrics = new Phase8Metrics(registry, "search");

        metrics.tombstoneEmitted();
        metrics.tombstoneApplied();
        metrics.staleEventRejected();
        metrics.reservationConflict();
        metrics.softDeleteFailure();
        metrics.projectionReplayFailure();

        assertThat(registry.counter("delivery.tombstones", "service", "search", "action", "emitted").count())
                .isEqualTo(1);
        assertThat(registry.counter("delivery.tombstones", "service", "search", "action", "applied").count())
                .isEqualTo(1);
        assertThat(registry.counter("delivery.events", "service", "search", "outcome", "stale_rejected").count())
                .isEqualTo(1);
        assertThat(registry.counter("delivery.reservations", "service", "search", "outcome", "conflict").count())
                .isEqualTo(1);
        assertThat(registry.counter("delivery.soft_deletes", "service", "search", "outcome", "failure").count())
                .isEqualTo(1);
        assertThat(registry.counter("delivery.projections", "service", "search", "outcome", "replay_failure").count())
                .isEqualTo(1);
    }
}
