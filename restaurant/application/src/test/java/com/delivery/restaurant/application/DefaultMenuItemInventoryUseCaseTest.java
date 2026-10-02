package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.application.api.InventoryStorePort.*;
import com.delivery.restaurant.domain.inventory.*;
import com.delivery.restaurant.domain.inventory.InventoryReservation.State;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultMenuItemInventoryUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void reserveLocksAscendingIdsAndValidatesWholeCartBeforeMutation() {
        var f = new Fixture();
        var result = f.core.reserve(command(List.of(line(12L, 1), line(11L, 2))));
        assertEquals("RESERVED", result.state()); assertEquals(NOW.plusMinutes(15), result.expiresAt());
        assertEquals(List.of("write", "items:[11, 12]", "stocks:[11, 12]", "change:11", "change:12", "insert"), f.calls);
        assertEquals(new InventoryCapacity(5, 2, 1L), f.stock.get(11L).capacity());
        assertEquals(new InventoryCapacity(5, 1, 1L), f.stock.get(12L).capacity());
        assertEquals(11L, result.items().get(0).menuItemId());
        var bad = new Fixture(); bad.stock.put(12L, new Stock(12L, new InventoryCapacity(0, 0, 0L)));
        assertThrows(IllegalArgumentException.class, () -> bad.core.reserve(command(List.of(line(11L, 2), line(12L, 1)))));
        assertEquals(new InventoryCapacity(5, 0, 0L), bad.stock.get(11L).capacity()); assertEquals(0, bad.writes);
    }

    @Test void exactReplayIsReadOnlyAndPayloadChangesAreRejected() {
        var f = new Fixture(); var command = command(List.of(line(11L, 2)));
        var initial = f.core.reserve(command); int writes = f.writes;
        assertEquals(initial, f.core.reserve(command)); assertEquals(writes, f.writes);
        for (InventoryReservationCommand changed : List.of(
                new InventoryReservationCommand(ID, 101L, 71L, 700L, 7L, command.items()),
                new InventoryReservationCommand(ID, 101L, 70L, 701L, 7L, command.items()),
                new InventoryReservationCommand(ID, 102L, 70L, 700L, 7L, command.items()),
                new InventoryReservationCommand(ID, 101L, 70L, 700L, 8L, command.items()),
                command(List.of(line(11L, 3))))) {
            assertThrows(IllegalArgumentException.class, () -> f.core.reserve(changed));
        }
        f.forcedOrder = reservation(UUID.randomUUID(), State.RESERVED, NOW.plusMinutes(15), List.of(new InventoryReservation.Line(11L, 2)));
        assertThrows(IllegalArgumentException.class, () -> f.core.reserve(command));
        f.reservations.clear();
        assertThrows(IllegalArgumentException.class, () -> f.core.reserve(command));
    }

    @Test void rejectsDuplicateStoredLinesAndInvalidReservationInputs() {
        var f = new Fixture();
        f.reservations.put(ID, reservation(ID, State.RESERVED, NOW.plusMinutes(15),
                List.of(new InventoryReservation.Line(11L, 1), new InventoryReservation.Line(11L, 1))));
        assertThrows(IllegalStateException.class, () -> f.core.reserve(command(List.of(line(11L, 2)))));
        for (InventoryReservationCommand invalid : Arrays.asList(null,
                new InventoryReservationCommand(null, 101L, null, null, 7L, List.of(line(11L, 1))),
                new InventoryReservationCommand(ID, null, null, null, 7L, List.of(line(11L, 1))),
                new InventoryReservationCommand(ID, 0L, null, null, 7L, List.of(line(11L, 1))),
                new InventoryReservationCommand(ID, 101L, null, null, null, List.of(line(11L, 1))),
                new InventoryReservationCommand(ID, 101L, null, null, 0L, List.of(line(11L, 1))))) {
            assertThrows(IllegalArgumentException.class, () -> f.core.reserve(invalid));
        }
        for (List<InventoryReservationLineCommand> invalid : Arrays.asList(null, List.<InventoryReservationLineCommand>of(),
                Arrays.<InventoryReservationLineCommand>asList((InventoryReservationLineCommand) null),
                List.of(line(null, 1)), List.of(line(0L, 1)), List.of(line(11L, null)), List.of(line(11L, 0)),
                List.of(line(11L, 100)), List.of(line(11L, 1), line(11L, 2)))) {
            assertThrows(IllegalArgumentException.class, () -> f.core.reserve(command(invalid)));
        }
        assertEquals(0, f.writes);
    }

    @Test void cartFailsClosedForMissingUnavailableAndForeignItems() {
        var f = new Fixture(); var command = command(List.of(line(11L, 1)));
        f.items.clear(); assertThrows(IllegalArgumentException.class, () -> f.core.reserve(command));
        for (Item invalid : List.of(new Item(11L, null, null, true), new Item(11L, 8L, 70L, true), new Item(11L, 7L, 70L, false))) {
            f.items.put(11L, invalid); assertThrows(IllegalArgumentException.class, () -> f.core.reserve(command));
        }
        f.items.put(11L, new Item(11L, 7L, 70L, true)); f.stock.clear();
        assertThrows(IllegalArgumentException.class, () -> f.core.reserve(command));
        assertEquals(0, f.writes);
    }

    @Test void commitThenReleaseCompensatesExactlyOnceAndTerminalReplaysDoNothing() {
        var f = new Fixture(); f.core.reserve(command(List.of(line(11L, 2))));
        assertEquals("COMMITTED", f.core.commit(ID, 101L).state());
        assertEquals(new InventoryCapacity(3, 0, 2L), f.stock.get(11L).capacity());
        int writes = f.writes;
        assertEquals("COMMITTED", f.core.commit(ID, 101L).state()); assertEquals(writes, f.writes);
        assertEquals("RELEASED", f.core.release(ID, 101L).state());
        assertEquals(new InventoryCapacity(5, 0, 3L), f.stock.get(11L).capacity());
        writes = f.writes;
        assertEquals("RELEASED", f.core.release(ID, 101L).state()); assertEquals(writes, f.writes);
        assertEquals("RELEASED", f.core.commit(ID, 101L).state());
    }

    @Test void expiryBoundaryNeverConsumesStockAndReservedReleaseOnlyFreesHold() {
        var f = new Fixture(); f.core.reserve(command(List.of(line(11L, 2))));
        f.reservations.put(ID, reservation(ID, State.RESERVED, NOW, List.of(new InventoryReservation.Line(11L, 2))));
        assertEquals("EXPIRED", f.core.commit(ID, 101L).state());
        assertEquals(new InventoryCapacity(5, 0, 2L), f.stock.get(11L).capacity());
        int writes = f.writes; f.core.release(ID, 101L); assertEquals(writes, f.writes);
        var held = new Fixture(); held.core.reserve(command(List.of(line(11L, 2))));
        assertEquals("RELEASED", held.core.release(ID, 101L).state());
        assertEquals(new InventoryCapacity(5, 0, 2L), held.stock.get(11L).capacity());
    }

    @Test void expiryRelocksAndRechecksCandidatesBeforeReleasingCapacity() {
        var f = new Fixture(); f.core.reserve(command(List.of(line(11L, 2))));
        var due = reservation(ID, State.RESERVED, NOW, List.of(new InventoryReservation.Line(11L, 2)));
        f.due = List.of(due, due); f.reservations.put(ID, due);
        assertEquals(1, f.core.expireReservations()); assertEquals(State.EXPIRED, f.reservations.get(ID).state());
        f.reservations.clear(); assertEquals(0, f.core.expireReservations());
        f.reservations.put(ID, reservation(ID, State.RESERVED, NOW.plusSeconds(1), due.lines()));
        assertEquals(0, f.core.expireReservations());
    }

    @Test void boundOrderAndCompleteLedgerAreRequiredForEveryTransition() {
        var f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.core.commit(null, 101L));
        assertThrows(IllegalArgumentException.class, () -> f.core.commit(ID, null));
        assertThrows(IllegalArgumentException.class, () -> f.core.commit(ID, 0L));
        assertThrows(IllegalArgumentException.class, () -> f.core.commit(ID, 101L));
        f.core.reserve(command(List.of(line(11L, 2))));
        assertThrows(IllegalArgumentException.class, () -> f.core.commit(ID, 102L));
        f.stock.clear(); assertThrows(IllegalStateException.class, () -> f.core.commit(ID, 101L));
        assertThrows(IllegalStateException.class, () -> f.core.release(ID, 101L));
    }

    @Test void previewAndReadsAreAdvisoryReadOnlyAndFailClosed() {
        var f = new Fixture();
        assertEquals(5, f.core.getInventory(11L).availableQuantity());
        assertTrue(f.core.availability(7L, 11L, 5).hasEnoughStock());
        assertFalse(f.core.availability(7L, 11L, 6).hasEnoughStock());
        assertEquals(5, f.core.availability(7L, 11L, 6).availableQuantity());
        for (Long invalid : Arrays.asList(null, 0L)) {
            assertThrows(IllegalArgumentException.class, () -> f.core.getInventory(invalid));
            assertFalse(f.core.availability(invalid, 11L, 1).hasEnoughStock());
            assertFalse(f.core.availability(7L, invalid, 1).hasEnoughStock());
        }
        for (Integer invalid : Arrays.asList(null, 0, 100)) assertFalse(f.core.availability(7L, 11L, invalid).hasEnoughStock());
        f.items.put(11L, new Item(11L, 8L, 70L, true)); assertEquals(0, f.core.availability(7L, 11L, 1).availableQuantity());
        f.items.clear(); assertFalse(f.core.availability(7L, 11L, 1).hasEnoughStock());
        f.items.put(11L, new Item(11L, null, 70L, true)); assertFalse(f.core.availability(7L, 11L, 1).hasEnoughStock());
        f.items.put(11L, new Item(11L, 7L, 70L, false)); assertFalse(f.core.availability(7L, 11L, 1).hasEnoughStock());
        f.items.put(11L, new Item(11L, 7L, 70L, true)); f.stock.clear();
        assertThrows(InventoryResourceNotFoundException.class, () -> f.core.getInventory(11L));
        assertFalse(f.core.availability(7L, 11L, 1).hasEnoughStock());
        f.stock.put(11L, new Stock(11L, new InventoryCapacity(0, 1, 0L))); assertFalse(f.core.availability(7L, 11L, 1).hasEnoughStock());
        assertTrue(f.calls.stream().allMatch("read"::equals));
    }

    @Test void inventoryEditsRequireAuthorizedOwnerAndCurrentRevision() {
        var f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.core.updateInventory(11L, null));
        assertThrows(IllegalArgumentException.class, () -> f.core.updateInventory(11L, edit(null, 0L, 70L, RestaurantActorRole.ADMIN)));
        assertThrows(IllegalArgumentException.class, () -> f.core.updateInventory(11L, edit(-1, 0L, 70L, RestaurantActorRole.ADMIN)));
        assertThrows(InventoryAccessDeniedException.class, () -> f.core.updateInventory(11L, edit(8, 0L, 70L, null)));
        for (Long invalid : Arrays.asList(null, 0L)) {
            assertThrows(InventoryAccessDeniedException.class, () -> f.core.updateInventory(11L, edit(8, 0L, invalid, RestaurantActorRole.ADMIN)));
        }
        assertThrows(InventoryAccessDeniedException.class, () -> f.core.updateInventory(11L, edit(8, 0L, 71L, RestaurantActorRole.SHOP_OWNER)));
        f.items.put(11L, new Item(11L, null, null, true));
        assertThrows(InventoryAccessDeniedException.class, () -> f.core.updateInventory(11L, edit(8, 0L, 70L, RestaurantActorRole.SHOP_OWNER)));
        f.items.clear(); assertThrows(InventoryResourceNotFoundException.class, () -> f.core.updateInventory(11L, edit(8, 0L, 70L, RestaurantActorRole.ADMIN)));
        f.items.put(11L, new Item(11L, 7L, 70L, true)); f.stock.clear();
        assertThrows(IllegalArgumentException.class, () -> f.core.updateInventory(11L, edit(8, 0L, 70L, RestaurantActorRole.ADMIN)));
        var created = f.core.updateInventory(11L, edit(8, null, 70L, RestaurantActorRole.ADMIN));
        assertEquals(8, created.onHandQuantity()); assertEquals(0L, created.revision());
        var updated = f.core.updateInventory(11L, edit(9, 0L, 70L, RestaurantActorRole.SHOP_OWNER));
        assertEquals(9, updated.onHandQuantity()); assertEquals(1L, updated.revision());
        assertThrows(IllegalArgumentException.class, () -> f.core.updateInventory(11L, edit(9, 0L, 70L, RestaurantActorRole.ADMIN)));
    }

    @Test void invalidTtlUsesExistingFifteenMinuteDefaultAndValidTtlIsRetained() {
        for (Duration ttl : Arrays.asList(null, Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMinutes(2))) {
            var f = new Fixture(); var core = new DefaultMenuItemInventoryUseCase(f, f, ttl, CLOCK);
            assertEquals(NOW.plus(ttl != null && !ttl.isNegative() && !ttl.isZero() ? ttl : Duration.ofMinutes(15)),
                    core.reserve(command(List.of(line(11L, 1)))).expiresAt());
        }
    }

    private static InventoryReservationCommand command(List<InventoryReservationLineCommand> lines) { return new InventoryReservationCommand(ID, 101L, 70L, 700L, 7L, lines); }
    private static InventoryReservationLineCommand line(Long id, Integer quantity) { return new InventoryReservationLineCommand(id, quantity); }
    private static UpdateMenuItemInventoryCommand edit(Integer quantity, Long revision, Long actor, RestaurantActorRole role) { return new UpdateMenuItemInventoryCommand(quantity, revision, actor, role); }
    private static InventoryReservation reservation(UUID id, State state, LocalDateTime expires, List<InventoryReservation.Line> lines) {
        return new InventoryReservation(id, 101L, 70L, 700L, 7L, state, expires, NOW, NOW, lines);
    }
    private static class Fixture implements InventoryStorePort, RestaurantTransactionPort {
        Map<Long, Item> items = new HashMap<>(); Map<Long, Stock> stock = new HashMap<>();
        Map<UUID, InventoryReservation> reservations = new HashMap<>();
        InventoryReservation forcedOrder; List<InventoryReservation> due = List.of(); List<String> calls = new ArrayList<>(); int writes;
        DefaultMenuItemInventoryUseCase core = new DefaultMenuItemInventoryUseCase(this, this, Duration.ofMinutes(15), CLOCK);
        Fixture() { for (long id : new long[]{11,12}) { items.put(id, new Item(id, 7L, 70L, true)); stock.put(id, new Stock(id, new InventoryCapacity(5, 0, 0L))); } }
        public <T> T required(Supplier<T> work) { calls.add("write"); return work.get(); }
        public <T> T repeatableRead(java.util.function.Supplier<T> operation) { return operation.get(); }
        public <T> T readOnly(Supplier<T> work) { calls.add("read"); return work.get(); }
        public Optional<InventoryReservation> findReservation(UUID id) { return Optional.ofNullable(reservations.get(id)); }
        public Optional<InventoryReservation> findReservationByOrder(Long id) { return forcedOrder != null ? Optional.of(forcedOrder) : reservations.values().stream().filter(r -> r.orderId().equals(id)).findFirst(); }
        public Optional<InventoryReservation> lockReservation(UUID id) { return findReservation(id); }
        public List<UUID> dueReservationIds(LocalDateTime now) { return due.stream().map(InventoryReservation::reservationId).toList(); }
        public List<Item> lockItems(List<Long> ids) { calls.add("items:" + ids); return ids.stream().map(items::get).filter(Objects::nonNull).toList(); }
        public Optional<Item> lockItem(Long id) { return findItem(id); }
        public Optional<Item> findItem(Long id) { return Optional.ofNullable(items.get(id)); }
        public List<Stock> lockStocks(List<Long> ids) { calls.add("stocks:" + ids); return ids.stream().map(stock::get).filter(Objects::nonNull).toList(); }
        public Optional<Stock> lockStock(Long id) { return findStock(id); }
        public Optional<Stock> findStock(Long id) { return Optional.ofNullable(stock.get(id)); }
        public void changeStock(Stock value) { calls.add("change:" + value.menuItemId()); stock.put(value.menuItemId(), value); writes++; }
        public Stock saveStock(Stock value) { stock.put(value.menuItemId(), value); writes++; return value; }
        public InventoryReservation insertReservation(InventoryReservation value) { calls.add("insert"); reservations.put(value.reservationId(), value); writes++; return value; }
        public void changeReservationState(UUID id, State state) { reservations.put(id, reservations.get(id).withState(state)); writes++; }
    }
}
