package com.delivery.tracking.application;
import com.delivery.tracking.application.api.*;
import com.delivery.tracking.domain.LocationHistoryOutcome;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DefaultLocationHistoryUseCaseTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC);
    private final Store store = new Store();
    private final DefaultLocationHistoryUseCase core = new DefaultLocationHistoryUseCase(store, store, 500, 90, clock);
    private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private LocationHistoryCommand event(UUID id, Long shipper, Long delivery, Boolean online, long time,
            Double lat, Double lon, String raw) {
        return new LocationHistoryCommand(shipper, lat, lon, online, time, id, delivery, 4.255, 8.555, 180.005, "WEBSOCKET", raw);
    }
    private LocationHistoryCommand valid() { return event(id, 42L, 100L, true, clock.millis(), 10.770006, 106.700006, "abc"); }
    private LocationHistoryReceiptFacts receipt(Long delivery, Long shipper, Instant time, LocationHistoryOutcome outcome, String hash) {
        return new LocationHistoryReceiptFacts(id, delivery, shipper, time, outcome, hash);
    }
    private LocationHistoryPoint point(Instant time, String lat) {
        return new LocationHistoryPoint(UUID.randomUUID(), 100L, 42L, time, new BigDecimal(lat),
                new BigDecimal("106.70001"), null, null, null, "REST");
    }
    @Test void firstPointIsClaimedRoundedAndCompletedWithinOneTransaction() {
        assertThat(core.record(valid())).isEqualTo(LocationHistoryOutcome.PERSISTED);
        assertThat(store.saved).isEqualTo(new LocationHistoryPoint(id, 100L, 42L, clock.instant(),
                new BigDecimal("10.77001"), new BigDecimal("106.70001"), new BigDecimal("4.26"),
                new BigDecimal("8.56"), new BigDecimal("180.01"), "WEBSOCKET"));
        assertThat(store.claimed.payloadFingerprint()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(store.calls).containsExactly("write", "receipt", "claim", "lock", "previous", "next", "save", "complete");
    }
    @Test void noDeliveryAndOfflineOutcomesSkipCoordinatesAndNeighbours() {
        assertThat(core.record(event(id, 42L, null, true, clock.millis(), null, null, "raw"))).isEqualTo(LocationHistoryOutcome.NO_DELIVERY);
        for (Boolean online : Arrays.asList(false, null))
            assertThat(core.record(event(id, 42L, 100L, online, clock.millis(), null, null, "raw"))).isEqualTo(LocationHistoryOutcome.OFFLINE_TOMBSTONE);
        assertThat(store.calls).doesNotContain("previous", "next", "save");
    }
    @Test void bothNeighboursFenceOutOfOrderSampling() {
        store.previous = point(clock.instant().minusSeconds(3), "10.77001");
        assertThat(core.record(valid())).isEqualTo(LocationHistoryOutcome.SAMPLED_OUT);
        store.previous = point(clock.instant().minusSeconds(20), "10.77001");
        store.next = point(clock.instant().plusSeconds(3), "10.77001");
        assertThat(core.record(valid())).isEqualTo(LocationHistoryOutcome.SAMPLED_OUT);
        store.next = point(clock.instant().plusSeconds(10), "10.77001");
        assertThat(core.record(valid())).isEqualTo(LocationHistoryOutcome.PERSISTED);
    }
    @Test void existingAndLostClaimReplayReturnCommittedOutcomeWithoutAnotherPoint() {
        var committed = receipt(100L, 42L, clock.instant(), LocationHistoryOutcome.SAMPLED_OUT, null);
        store.existing = committed;
        assertThat(core.record(valid())).isEqualTo(LocationHistoryOutcome.SAMPLED_OUT);
        assertThat(store.calls).containsExactly("write", "receipt");
        store.calls.clear(); store.existing = null; store.afterClaim = committed; store.claimResult = 0;
        assertThat(core.record(valid())).isEqualTo(LocationHistoryOutcome.SAMPLED_OUT);
        assertThat(store.calls).containsExactly("write", "receipt", "claim", "receipt");
    }
    @Test void replayIdentityPayloadPendingAndMissingCommittedClaimFailClosed() {
        for (var bad : List.of(
                receipt(101L, 42L, clock.instant(), LocationHistoryOutcome.PERSISTED, null),
                receipt(100L, 43L, clock.instant(), LocationHistoryOutcome.PERSISTED, null),
                receipt(100L, 42L, clock.instant().minusSeconds(1), LocationHistoryOutcome.PERSISTED, null))) {
            store.existing = bad;
            assertThatThrownBy(() -> core.record(valid())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contradictory identity");
        }
        store.existing = receipt(100L, 42L, clock.instant(), LocationHistoryOutcome.PERSISTED, "changed");
        assertThatThrownBy(() -> core.record(valid())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contradictory payload");
        store.existing = receipt(100L, 42L, clock.instant(), LocationHistoryOutcome.PENDING, null);
        assertThatThrownBy(() -> core.record(valid())).isInstanceOf(IllegalStateException.class).hasMessageContaining("remained pending");
        store.existing = null; store.claimResult = 0;
        assertThatThrownBy(() -> core.record(valid())).isInstanceOf(IllegalStateException.class).hasMessageContaining("without a committed row");
        store.claimResult = 1; store.completeResult = 0;
        assertThatThrownBy(() -> core.record(valid())).isInstanceOf(IllegalStateException.class).hasMessageContaining("not pending at completion");
    }
    @Test void invalidIdentityPayloadAndFutureTimeFailBeforeReceiptAccess() {
        var invalid = Arrays.asList(null,
                event(null, 42L, 100L, true, clock.millis(), 10.77, 106.7, "raw"),
                event(id, null, 100L, true, clock.millis(), 10.77, 106.7, "raw"),
                event(id, 0L, 100L, true, clock.millis(), 10.77, 106.7, "raw"),
                event(id, 42L, 100L, true, 0, 10.77, 106.7, "raw"),
                event(id, 42L, 100L, true, clock.millis()+300001, 10.77, 106.7, "raw"),
                event(id, 42L, 0L, true, clock.millis(), 10.77, 106.7, "raw"),
                event(id, 42L, 100L, true, clock.millis(), 10.77, 106.7, null),
                event(id, 42L, 100L, true, clock.millis(), 10.77, 106.7, " "));
        for (var command : invalid) assertThatThrownBy(() -> core.record(command)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.calls).containsOnly("write");
        assertThat(core.record(event(id, 42L, null, true, clock.millis()+300000, null, null, "boundary")))
                .isEqualTo(LocationHistoryOutcome.NO_DELIVERY);
    }
    @Test void onlineInvalidCoordinatesFailAfterClaimAndBeforePointWrite() {
        for (Double bad : Arrays.asList(null, Double.NaN, Double.POSITIVE_INFINITY, -91.0, 91.0))
            assertThatThrownBy(() -> core.record(event(id, 42L, 100L, true, clock.millis(), bad, 106.7, "raw")))
                    .isInstanceOf(IllegalArgumentException.class);
        for (Double bad : Arrays.asList(null, Double.NaN, Double.NEGATIVE_INFINITY, -181.0, 181.0))
            assertThatThrownBy(() -> core.record(event(id, 42L, 100L, true, clock.millis(), 10.77, bad, "raw")))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.saved).isNull();
    }
    @Test void queriesAreReadOnlyBoundedAndCleanupUsesOneWriteTransactionAndClock() {
        core.byDelivery(100, 9999); assertThat(store.size).isEqualTo(500);
        core.byDelivery(100, -1); assertThat(store.size).isEqualTo(1);
        assertThatThrownBy(() -> core.byDelivery(0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThat(core.cleanupExpired()).isEqualTo(new LocationHistoryCleanupResult(4, 5));
        assertThat(store.cutoff).isEqualTo(clock.instant().minusSeconds(90L*86400));
        var clamped = new DefaultLocationHistoryUseCase(store, store, 0, 0, clock);
        clamped.byDelivery(100, 9999); assertThat(store.size).isEqualTo(1);
        clamped.cleanupExpired(); assertThat(store.cutoff).isEqualTo(clock.instant().minusSeconds(86400));
        assertThat(store.calls).contains("read", "write", "deleteHistory", "deleteReceipts");
        assertThatThrownBy(() -> new DefaultLocationHistoryUseCase(null, store, 1, 1, clock)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultLocationHistoryUseCase(store, null, 1, 1, clock)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultLocationHistoryUseCase(store, store, 1, 1, null)).isInstanceOf(NullPointerException.class);
    }
    private static final class Store implements LocationHistoryStorePort, LocationHistoryTransactionPort {
        boolean active; int claimResult=1, completeResult=1, size;
        LocationHistoryReceiptFacts existing, afterClaim, claimed;
        LocationHistoryPoint previous, next, saved; Instant cutoff;
        final List<String> calls = new ArrayList<>();
        public <T> T required(Supplier<T> action) { return execute("write", action); }
        public <T> T readOnly(Supplier<T> action) { return execute("read", action); }
        private <T> T execute(String mode, Supplier<T> action) { calls.add(mode); active=true; try{return action.get();}finally{active=false;} }
        private void call(String name) { assertThat(active).isTrue(); calls.add(name); }
        public Optional<LocationHistoryReceiptFacts> receipt(UUID id) { call("receipt"); return Optional.ofNullable(existing); }
        public int claim(LocationHistoryReceiptFacts value) { call("claim"); claimed=value; existing=afterClaim; return claimResult; }
        public int complete(UUID id, LocationHistoryOutcome value) { call("complete"); return completeResult; }
        public void lockSampling(Long delivery, Long shipper) { call("lock"); }
        public Optional<LocationHistoryPoint> previous(Long delivery, Long shipper, Instant time) { call("previous"); return Optional.ofNullable(previous); }
        public Optional<LocationHistoryPoint> next(Long delivery, Long shipper, Instant time) { call("next"); return Optional.ofNullable(next); }
        public void save(LocationHistoryPoint point) { call("save"); saved=point; }
        public List<LocationHistoryPoint> byDelivery(long delivery,int size) { call("query"); this.size=size; return List.of(); }
        public int deleteHistoryOlderThan(Instant cutoff) { call("deleteHistory"); this.cutoff=cutoff; return 4; }
        public int deleteReceiptsOlderThan(Instant cutoff) { call("deleteReceipts"); assertThat(cutoff).isEqualTo(this.cutoff); return 5; }
    }
}
