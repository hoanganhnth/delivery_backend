package com.delivery.tracking.application;

import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultShipperAvailabilityUseCaseTest {
    private static final PublisherExpiryClaim CLAIM = new PublisherExpiryClaim(new PublisherLease(7, "expired", 3), 12345);
    @Test void expiryMutatesConditionallyBeforeKafkaAndFanoutWithOrWithoutCachedFacts() {
        for (boolean cached : new boolean[]{false, true}) {
            var f = new Fixture();
            if (cached) f.cached = new CachedShipperLocation(7L, 10.77, null, null, null, null, null);
            assertThat(f.core.markOfflineIfExpired(CLAIM)).isTrue();
            assertThat(f.operations).containsExactly("read", "conditional", "OFFLINE_TOMBSTONE", "broadcast");
            assertThat(f.claim).isSameAs(CLAIM); assertThat(f.saved != null).isEqualTo(cached);
            assertThat(f.published.facts().shipperId()).isEqualTo(7);
            assertThat(f.published.timestamp()).isEqualTo(Instant.parse("2026-10-03T00:00:00Z"));
        }
    }
    @Test void fencedExpiryCannotPublishAndStorageOrPublicationFailureStaysRetryable() {
        var fenced = new Fixture(); fenced.admitted = false;
        assertThat(fenced.core.markOfflineIfExpired(CLAIM)).isFalse();
        assertThat(fenced.operations).containsExactly("read", "conditional");
        assertThat(fenced.published).isNull(); assertThat(fenced.broadcast).isNull();
        for (String stage : List.of("read", "conditional", "OFFLINE_TOMBSTONE", "broadcast")) {
            var f = new Fixture(); f.failAt = stage;
            assertThatThrownBy(() -> f.core.markOfflineIfExpired(CLAIM)).isSameAs(f.failure);
            if (!stage.equals("broadcast")) assertThat(f.broadcast).isNull();
        }
        assertThatThrownBy(() -> new Fixture().core.markOfflineIfExpired(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Fixture().core.markOfflineIfExpired(new PublisherExpiryClaim(null, 0)))
                .isInstanceOf(NullPointerException.class);
    }
    @Test void keepsCachedFactsAndWritesBeforePublishingTheTimestampedTombstone() {
        var f = new Fixture();
        f.cached = new CachedShipperLocation(7L, 10.77, 106.7, 3.5, 12.0, 90.0, 1.2);
        var result = f.core.markOffline(7L);
        assertThat(result.facts()).isSameAs(f.cached);
        assertThat(result.timestamp()).isEqualTo(Instant.parse("2026-10-03T00:00:00Z"));
        assertThat(f.operations).containsExactly("read", "save", "OFFLINE_TOMBSTONE");
        assertThat(f.saved).isSameAs(result); assertThat(f.published).isSameAs(result);
    }
    @Test void absentCacheRemovesMembershipAndStillPublishesAnIdentityOnlyTombstone() {
        var f = new Fixture(); var result = f.core.markOffline(7L);
        assertThat(result.facts()).isEqualTo(new CachedShipperLocation(7L, null, null, null, null, null, null));
        assertThat(f.operations).containsExactly("read", "remove", "OFFLINE_TOMBSTONE");
        assertThat(f.saved).isNull(); assertThat(f.published).isSameAs(result);
    }
    @Test void partialCoordinatesAreRetainedAndDoNotPreventOffline() {
        var f = new Fixture(); f.cached = new CachedShipperLocation(7L, 10.77, null, null, null, null, null);
        assertThat(f.core.markOffline(7L).facts()).isEqualTo(f.cached);
        assertThat(f.operations).containsExactly("read", "save", "OFFLINE_TOMBSTONE");
    }
    @Test void readOrWriteFailureCannotPublishAndBrokerFailurePropagatesAfterSafeMutation() {
        for (String stage : List.of("read", "save", "remove", "OFFLINE_TOMBSTONE")) {
            var f = new Fixture(); f.failAt = stage;
            if (stage.equals("save")) f.cached = new CachedShipperLocation(7L, 10.77, 106.7, null, null, null, null);
            assertThatThrownBy(() -> f.core.markOffline(7L)).isSameAs(f.failure);
            if (!stage.equals("OFFLINE_TOMBSTONE")) assertThat(f.published).isNull();
            else assertThat(f.operations).containsExactly("read", "remove", "OFFLINE_TOMBSTONE");
        }
    }
    @Test void invalidShipperAndDependenciesAreRejectedBeforeSideEffects() {
        var f = new Fixture();
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> f.core.markOffline(id)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("shipperId must be positive");
        }
        assertThat(f.operations).isEmpty();
        assertThatThrownBy(() -> new DefaultShipperAvailabilityUseCase(null, f)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultShipperAvailabilityUseCase(f, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultShipperAvailabilityUseCase(f, f, null)).isInstanceOf(NullPointerException.class);
    }
    @Test void explicitOfflineBroadcastsOnlyAfterSuccessfulKafkaPublication() {
        var f = new Fixture(); var result = f.core.markOfflineAndBroadcast(7L);
        assertThat(f.operations).containsExactly("read", "remove", "OFFLINE_TOMBSTONE", "broadcast");
        assertThat(f.broadcast).isSameAs(result);
        var failed = new Fixture(); failed.failAt = "OFFLINE_TOMBSTONE";
        assertThatThrownBy(() -> failed.core.markOfflineAndBroadcast(7L)).isSameAs(failed.failure);
        assertThat(failed.broadcast).isNull();
        var failedFanout = new Fixture(); failedFanout.failAt = "broadcast";
        assertThatThrownBy(() -> failedFanout.core.markOfflineAndBroadcast(7L)).isSameAs(failedFanout.failure);
        assertThat(failedFanout.published).isNotNull();
    }
    private static final class Fixture implements ShipperAvailabilityStorePort, ShipperAvailabilityEventPort {
        CachedShipperLocation cached; OfflineShipperLocation saved, published, broadcast; String failAt;
        boolean admitted = true; PublisherExpiryClaim claim;
        final List<String> operations = new ArrayList<>();
        final RuntimeException failure = new IllegalStateException("Boundary unavailable");
        final DefaultShipperAvailabilityUseCase core = new DefaultShipperAvailabilityUseCase(this, this,
                Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        void step(String stage) { operations.add(stage); if (stage.equals(failAt)) throw failure; }
        public Optional<CachedShipperLocation> findCached(Long id) { assertThat(id).isEqualTo(7L); step("read"); return Optional.ofNullable(cached); }
        public void saveOffline(Long id, OfflineShipperLocation location) { assertThat(id).isEqualTo(7L); step("save"); saved = location; }
        public boolean applyOfflineIfExpired(com.delivery.tracking.domain.PublisherExpiryClaim claim, Optional<OfflineShipperLocation> row) {
            step("conditional"); this.claim = claim;
            if (admitted) saved = row.orElse(null);
            return admitted;
        }
        public void remove(Long id) { assertThat(id).isEqualTo(7L); step("remove"); }
        public void publish(OfflineShipperLocation location, String source) { step(source); published = location; }
        public void broadcast(OfflineShipperLocation location) { step("broadcast"); broadcast = location; }
    }
}
