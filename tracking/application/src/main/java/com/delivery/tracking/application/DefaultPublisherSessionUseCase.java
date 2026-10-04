package com.delivery.tracking.application;

import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.PublisherLease;
import com.delivery.tracking.domain.PublisherExpiryClaim;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Owns generation/grace admission and retryable offline recovery; Redis retains atomic fences. */
public final class DefaultPublisherSessionUseCase implements PublisherSessionUseCase {
    private final PublisherLeaseStorePort leases;
    private final ShipperAvailabilityUseCase availability;
    private final PublisherTaskSchedulePort tasks;
    private final PublisherLeaseIncidentPort incidents;
    private final Clock clock;
    private final long graceSeconds;
    private final long leaseSeconds;
    private final long claimSeconds;

    public DefaultPublisherSessionUseCase(PublisherLeaseStorePort leases, ShipperAvailabilityUseCase availability,
            PublisherTaskSchedulePort tasks, PublisherLeaseIncidentPort incidents,
            long graceSeconds, long leaseSeconds, long claimSeconds) {
        this(leases, availability, tasks, incidents, graceSeconds, leaseSeconds, claimSeconds, Clock.systemUTC());
    }

    public DefaultPublisherSessionUseCase(PublisherLeaseStorePort leases, ShipperAvailabilityUseCase availability,
            PublisherTaskSchedulePort tasks, PublisherLeaseIncidentPort incidents,
            long graceSeconds, long leaseSeconds, long claimSeconds, Clock clock) {
        this.leases = Objects.requireNonNull(leases, "leases");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.graceSeconds = Math.max(0, graceSeconds);
        this.leaseSeconds = Math.max(this.graceSeconds + 1, leaseSeconds);
        this.claimSeconds = Math.max(1, claimSeconds);
    }

    @Override public PublisherLease acquire(Long shipperId, String sessionId) {
        return leases.acquire(shipperId, sessionId, leaseSeconds);
    }
    @Override public boolean refreshIfCurrent(PublisherLease lease) {
        return leases.refreshIfCurrent(lease, leaseSeconds);
    }
    @Override public void disconnected(PublisherLease lease) {
        if (!leases.releaseForGraceIfCurrent(lease, graceSeconds)) return;
        tasks.schedule(() -> afterGrace(lease), Instant.now(clock).plusSeconds(graceSeconds));
    }
    private void afterGrace(PublisherLease lease) {
        try {
            var claim = leases.claimIfExpired(lease, claimSeconds);
            if (claim != null) reconcile(claim);
        } catch (Exception failure) {
            incidents.graceFailed(lease, failure);
        }
    }
    @Override public void sweepExpired(int batchSize) {
        for (var claim : leases.claimExpired(Math.max(1, batchSize), claimSeconds)) {
            try {
                if (reconcile(claim)) incidents.expiredOffline(claim.lease());
            } catch (Exception failure) {
                incidents.sweepFailed(claim.lease(), failure);
            }
        }
    }
    private boolean reconcile(PublisherExpiryClaim claim) {
        boolean transitioned = leases.shouldMarkOfflineAfterGrace(claim.lease())
                && availability.markOfflineIfExpired(claim);
        leases.completeClaim(claim);
        return transitioned;
    }
}
