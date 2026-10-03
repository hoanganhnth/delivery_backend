package com.delivery.tracking.application;

import com.delivery.tracking.application.api.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultShipperIdentityInboxUseCaseTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC);
    private final Store store = new Store();
    private final DefaultShipperIdentityInboxUseCase core = new DefaultShipperIdentityInboxUseCase(store, clock);
    private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private ApplyShipperIdentityCommand command(UUID id, long version, String raw) {
        return new ApplyShipperIdentityCommand(id, "shipper.identity.upserted", 100L, 200L, 300L, version, raw);
    }
    @Test void initialSnapshotRetainsAnyPositiveVersionAndExactServerTimeAndRawHash() {
        core.apply(command(id, 7, "abc"));
        assertThat(store.mapping).isEqualTo(new ShipperIdentityMapping(100L, 200L, 300L, 7L, LocalDateTime.now(clock)));
        assertThat(store.receipt).isEqualTo(new ShipperIdentityReceipt(id, "shipper.identity.upserted", 100L,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", LocalDateTime.now(clock)));
        assertThat(store.calls).containsExactly("transaction", "receipt", "mapping", "saveMapping", "saveReceipt");
    }
    @Test void exactReplayDoesNotReadOrRewriteProjectionOrReceipt() {
        core.apply(command(id, 1, "abc")); store.calls.clear();
        core.apply(command(id, 1, "abc"));
        assertThat(store.calls).containsExactly("transaction", "receipt");
    }
    @Test void reusedEventFailsOnTypePrincipalOrExactRawPayloadConflict() {
        for (var prior : List.of(
                new ShipperIdentityReceipt(id, "other", 100L, "hash", LocalDateTime.now(clock)),
                new ShipperIdentityReceipt(id, "shipper.identity.upserted", 999L, "hash", LocalDateTime.now(clock)),
                new ShipperIdentityReceipt(id, "shipper.identity.upserted", 100L, "hash", LocalDateTime.now(clock)))) {
            store.receipt = prior; store.calls.clear();
            assertThatThrownBy(() -> core.apply(command(id, 1, "abc")))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Conflicting shipper identity event reuse");
            assertThat(store.calls).containsExactly("transaction", "receipt");
        }
    }
    @Test void staleVersionReceiptsWithoutReplacingCurrentMapping() {
        store.mapping = new ShipperIdentityMapping(100L, 201L, 301L, 3L, LocalDateTime.now(clock));
        var previous = store.mapping;
        core.apply(command(id, 2, "stale"));
        assertThat(store.mapping).isSameAs(previous);
        assertThat(store.receipt.payloadFingerprint()).hasSize(64);
        assertThat(store.calls).doesNotContain("saveMapping");
    }
    @Test void equalAndNextVersionRetainExistingAdmissionAndNullableLegacyFactsCanBeReplaced() {
        for (Long version : Arrays.asList(1L, 2L, null)) {
            store.mapping = new ShipperIdentityMapping(100L, 201L, 301L, version, LocalDateTime.now(clock));
            store.receipt = null; core.apply(command(id, 2, "accepted"));
            assertThat(store.mapping.mappingVersion()).isEqualTo(2);
            assertThat(store.mapping.shipperId()).isEqualTo(300);
        }
    }
    @Test void gapFailsBeforeWritesAndPropagatesStorageErrors() {
        store.mapping = new ShipperIdentityMapping(100L, 200L, 300L, 1L, LocalDateTime.now(clock));
        assertThatThrownBy(() -> core.apply(command(id, 3, "gap")))
                .isInstanceOf(IllegalStateException.class).hasMessage("Shipper identity mapping version gap");
        assertThat(store.calls).doesNotContain("saveMapping", "saveReceipt");
        store.failure = new IllegalStateException("storage down");
        assertThatThrownBy(() -> core.apply(command(id, 2, "next"))).isSameAs(store.failure);
    }
    @Test void invalidEventIdentityFailsBeforeTransaction() {
        var valid = command(id, 1, "abc");
        var invalid = Arrays.asList(null,
                new ApplyShipperIdentityCommand(null, valid.eventType(), 100L, 200L, 300L, 1, "abc"),
                new ApplyShipperIdentityCommand(id, null, 100L, 200L, 300L, 1, "abc"),
                new ApplyShipperIdentityCommand(id, "wrong", 100L, 200L, 300L, 1, "abc"),
                new ApplyShipperIdentityCommand(id, valid.eventType(), null, 200L, 300L, 1, "abc"),
                new ApplyShipperIdentityCommand(id, valid.eventType(), 100L, null, 300L, 1, "abc"),
                new ApplyShipperIdentityCommand(id, valid.eventType(), 100L, 200L, null, 1, "abc"),
                command(id, 0, "abc"));
        for (var event : invalid) assertThatThrownBy(() -> core.apply(event))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid shipper identity event");
        assertThatThrownBy(() -> core.apply(command(id, 1, null))).isInstanceOf(NullPointerException.class);
        assertThat(store.calls).isEmpty();
        assertThatThrownBy(() -> new DefaultShipperIdentityInboxUseCase(null, clock)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultShipperIdentityInboxUseCase(store, null)).isInstanceOf(NullPointerException.class);
    }
    private static class Store implements ShipperIdentityInboxStorePort {
        ShipperIdentityMapping mapping; ShipperIdentityReceipt receipt;
        RuntimeException failure; boolean transaction;
        final List<String> calls = new ArrayList<>();
        public void atomically(UUID eventId, Long principalId, Runnable operation) {
            calls.add("transaction"); transaction = true;
            try { operation.run(); } finally { transaction = false; }
        }
        private void call(String name) { assertThat(transaction).isTrue(); calls.add(name); }
        public Optional<ShipperIdentityReceipt> receipt(UUID id) { call("receipt"); return Optional.ofNullable(receipt); }
        public Optional<ShipperIdentityMapping> mapping(Long id) { call("mapping"); return Optional.ofNullable(mapping); }
        public void saveMapping(ShipperIdentityMapping value) { call("saveMapping"); if (failure != null) throw failure; mapping = value; }
        public void saveReceipt(ShipperIdentityReceipt value) { call("saveReceipt"); receipt = value; }
    }
}
