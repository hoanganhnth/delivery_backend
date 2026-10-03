package com.delivery.tracking.application;

import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultPublisherSessionUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Test void acquireAndRefreshUseExistingTtlAndForwardCurrentGenerationResult() {
        var f = new Fixture();
        assertThat(f.core.acquire(7L, "session")).isEqualTo(f.lease);
        assertThat(f.ttl).isEqualTo(120);
        assertThat(f.core.refreshIfCurrent(f.lease)).isTrue();
        f.refresh = false; assertThat(f.core.refreshIfCurrent(f.lease)).isFalse();
    }
    @Test void normalizesConfiguredGraceTtlAndClaimDurationWithoutChangingDefaults() {
        var f = new Fixture(); var core = f.core(30, 0, 0);
        core.acquire(7L, "session"); assertThat(f.ttl).isEqualTo(31);
        core.disconnected(f.lease); f.task.run(); assertThat(f.claimSeconds).isEqualTo(1);
        var negative = new Fixture(); negative.core(-1, 0, -1).disconnected(negative.lease);
        assertThat(negative.grace).isZero(); assertThat(negative.deadline).isEqualTo(NOW);
    }
    @Test void graceChecksClaimAndGenerationBeforeOfflineFanoutAndCompletion() {
        var f = new Fixture();
        f.core.disconnected(f.lease);
        assertThat(f.deadline).isEqualTo(NOW.plusSeconds(30));
        assertThat(f.operations).containsExactly("release", "schedule");
        f.task.run();
        assertThat(f.operations).containsExactly("release", "schedule", "claim", "fence", "offline:7", "fanout:7", "complete:7");
        assertThat(f.completed).containsExactly(f.claim);
    }
    @Test void supersededDisconnectAndReconnectDuringGraceNeverMarkOffline() {
        var superseded = new Fixture(); superseded.released = false;
        superseded.core.disconnected(superseded.lease);
        assertThat(superseded.operations).containsExactly("release"); assertThat(superseded.task).isNull();
        var reconnected = new Fixture(); reconnected.claim = null;
        reconnected.core.disconnected(reconnected.lease); reconnected.task.run();
        assertThat(reconnected.operations).containsExactly("release", "schedule", "claim");
    }
    @Test void activeOrNewerGenerationCompletesOnlyItsClaimWithoutOffline() {
        var f = new Fixture(); f.fenced = false;
        f.core.disconnected(f.lease); f.task.run();
        assertThat(f.operations).containsExactly("release", "schedule", "claim", "fence", "complete:7");
    }
    @Test void graceFailureRetainsRetryableClaimAndReportsTheOriginalError() {
        for (String stage : List.of("claim", "fence", "offline:7", "fanout:7", "complete:7")) {
            var f = new Fixture(); f.failAt = stage;
            f.core.disconnected(f.lease); f.task.run();
            assertThat(f.reported).isSameAs(f.failure); assertThat(f.completed).isEmpty();
            assertThat(f.operations.get(f.operations.size() - 1)).isEqualTo("grace-error:7");
        }
    }
    @Test void sweepFailureDoesNotCompleteFailedClaimOrPreventOtherClaimsFromRecovering() {
        var f = new Fixture(); var second = new PublisherExpiryClaim(new PublisherLease(8, "second", 1), 23456);
        f.claims = List.of(f.claim, second); f.failAt = "offline:7";
        f.core.sweepExpired(100);
        assertThat(f.batch).isEqualTo(100); assertThat(f.claimSeconds).isEqualTo(30);
        assertThat(f.completed).containsExactly(second);
        assertThat(f.operations).containsExactly("batch", "fence", "offline:7", "sweep-error:7", "fence", "offline:8", "fanout:8", "complete:8", "expired:8");
        assertThat(f.reported).isSameAs(f.failure);
    }
    @Test void sweepHandlesEmptyOrSupersededClaimsAndKeepsBatchAcquisitionFailureVisible() {
        var f = new Fixture(); f.fenced = false; f.core.sweepExpired(0);
        assertThat(f.batch).isEqualTo(1); assertThat(f.operations).containsExactly("batch", "fence", "complete:7");
        var empty = new Fixture(); empty.claims = List.of(); empty.core.sweepExpired(100);
        assertThat(empty.operations).containsExactly("batch");
        var unavailable = new Fixture(); unavailable.failAt = "batch";
        assertThatThrownBy(() -> unavailable.core.sweepExpired(100)).isSameAs(unavailable.failure);
        assertThat(unavailable.reported).isNull();
    }
    @Test void rejectedSchedulerLeavesDurableReleaseAndFailureVisibleToCaller() {
        var f = new Fixture(); f.failAt = "schedule";
        assertThatThrownBy(() -> f.core.disconnected(f.lease)).isSameAs(f.failure);
        assertThat(f.operations).containsExactly("release", "schedule"); assertThat(f.task).isNull();
    }
    @Test void acquireAndRefreshFailuresAndMissingDependenciesRemainVisible() {
        var f = new Fixture(); f.failAt = "acquire";
        assertThatThrownBy(() -> f.core.acquire(7L, "session")).isSameAs(f.failure);
        f.failAt = "refresh"; assertThatThrownBy(() -> f.core.refreshIfCurrent(f.lease)).isSameAs(f.failure);
        assertThatThrownBy(() -> new DefaultPublisherSessionUseCase(null, f, f, f, 30, 120, 30)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultPublisherSessionUseCase(f, null, f, f, 30, 120, 30)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultPublisherSessionUseCase(f, f, null, f, 30, 120, 30)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultPublisherSessionUseCase(f, f, f, null, 30, 120, 30)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultPublisherSessionUseCase(f, f, f, f, 30, 120, 30, null)).isInstanceOf(NullPointerException.class);
    }
    private static final class Fixture implements PublisherLeaseStorePort, PublisherTaskSchedulePort,
            PublisherLeaseIncidentPort, ShipperAvailabilityUseCase {
        final PublisherLease lease = new PublisherLease(7, "session", 3);
        PublisherExpiryClaim claim = new PublisherExpiryClaim(lease, 12345);
        List<PublisherExpiryClaim> claims = List.of(claim);
        final OfflineShipperLocation offline = new OfflineShipperLocation(new CachedShipperLocation(7L, null, null, null, null, null, null), LocalDateTime.of(2026, 10, 3, 7, 0));
        final List<String> operations = new ArrayList<>(); final List<PublisherExpiryClaim> completed = new ArrayList<>();
        final RuntimeException failure = new IllegalStateException("Boundary unavailable");
        String failAt; Exception reported; boolean released = true, refresh = true, fenced = true;
        long ttl, grace, claimSeconds; int batch; Runnable task; Instant deadline;
        final DefaultPublisherSessionUseCase core = core(30, 120, 30);
        DefaultPublisherSessionUseCase core(long grace, long ttl, long claim) {
            return new DefaultPublisherSessionUseCase(this, this, this, this, grace, ttl, claim, Clock.fixed(NOW, ZoneOffset.UTC));
        }
        void step(String stage) { operations.add(stage); if (stage.equals(failAt)) throw failure; }
        public PublisherLease acquire(Long id, String session, long ttl) { step("acquire"); this.ttl = ttl; return lease; }
        public boolean refreshIfCurrent(PublisherLease lease, long ttl) { step("refresh"); this.ttl = ttl; return refresh; }
        public boolean releaseForGraceIfCurrent(PublisherLease lease, long grace) { step("release"); this.grace = grace; return released; }
        public boolean shouldMarkOfflineAfterGrace(PublisherLease lease) { step("fence"); return fenced; }
        public List<PublisherExpiryClaim> claimExpired(int batch, long claim) { step("batch"); this.batch = batch; claimSeconds = claim; return claims; }
        public PublisherExpiryClaim claimIfExpired(PublisherLease lease, long seconds) { step("claim"); claimSeconds = seconds; return claim; }
        public boolean completeClaim(PublisherExpiryClaim claim) { step("complete:" + claim.lease().shipperId()); completed.add(claim); return true; }
        public void schedule(Runnable task, Instant deadline) { step("schedule"); this.task = task; this.deadline = deadline; }
        public OfflineShipperLocation markOffline(Long id) { throw new AssertionError("Recovery must include distributed fanout"); }
        public OfflineShipperLocation markOfflineAndBroadcast(Long id) { step("offline:" + id); step("fanout:" + id); return offline; }
        public void graceFailed(PublisherLease lease, Exception failure) { operations.add("grace-error:" + lease.shipperId()); reported = failure; }
        public void sweepFailed(PublisherLease lease, Exception failure) { operations.add("sweep-error:" + lease.shipperId()); reported = failure; }
        public void expiredOffline(PublisherLease lease) { step("expired:" + lease.shipperId()); }
    }
}
