package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.application.api.InventoryReceiptPort.Receipt;
import com.delivery.restaurant.domain.inventory.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultInventoryOrderEventUseCaseTest {
    private static final UUID EVENT = UUID.randomUUID(), RESERVATION = UUID.randomUUID();

    @Test void recordsReceiptBeforeCommittingOrReleasingWithinOneTransaction() {
        for (var source : InventoryOrderSource.values()) {
            var f = new Fixture(); f.core.consume(command(source, RESERVATION));
            String transition = source == InventoryOrderSource.ORDER_CREATED ? "commit" : "release";
            assertEquals(List.of("transaction", "receipt", transition), f.calls);
            assertEquals(source == InventoryOrderSource.ORDER_CREATED ? InventoryOrderAction.COMMIT : InventoryOrderAction.RELEASE,
                    f.rows.get(EVENT).action());
        }
    }

    @Test void exactReplayNeverTransitionsInventoryTwice() {
        var f = new Fixture(); var command = command(InventoryOrderSource.ORDER_CREATED, RESERVATION);
        f.core.consume(command); f.calls.clear(); f.core.consume(command);
        assertEquals(List.of("transaction", "receipt", "find"), f.calls);
    }

    @Test void everyReceiptBindingMustMatchForReplay() {
        var original = command(InventoryOrderSource.ORDER_CREATED, RESERVATION);
        for (Receipt contradictory : List.of(
                new Receipt(EVENT, "other-topic", InventoryOrderAction.COMMIT, 101L, RESERVATION, "hash"),
                new Receipt(EVENT, "order.created", InventoryOrderAction.RELEASE, 101L, RESERVATION, "hash"),
                new Receipt(EVENT, "order.created", InventoryOrderAction.COMMIT, 102L, RESERVATION, "hash"),
                new Receipt(EVENT, "order.created", InventoryOrderAction.COMMIT, 101L, UUID.randomUUID(), "hash"),
                new Receipt(EVENT, "order.created", InventoryOrderAction.COMMIT, 101L, RESERVATION, "other-hash"))) {
            var f = new Fixture(); f.rows.put(EVENT, contradictory);
            assertThrows(IllegalArgumentException.class, () -> f.core.consume(original));
            assertFalse(f.calls.contains("commit")); assertFalse(f.calls.contains("release"));
        }
    }

    @Test void eventWithoutReservationStillHasAReplaySafeReceipt() {
        var f = new Fixture(); var command = command(InventoryOrderSource.ORDER_CANCELLED, null);
        f.core.consume(command); f.core.consume(command);
        assertEquals(1, f.rows.size()); assertNull(f.rows.get(EVENT).reservationId());
        assertFalse(f.calls.contains("commit")); assertFalse(f.calls.contains("release"));
    }

    @Test void missingCommittedReceiptIsAnErrorAndTransitionFailuresPropagate() {
        var f = new Fixture(); f.forceDuplicate = true;
        assertThrows(IllegalStateException.class, () -> f.core.consume(command(InventoryOrderSource.ORDER_CREATED, RESERVATION)));
        assertFalse(f.calls.contains("commit"));
        var failed = new Fixture(); failed.failTransition = true;
        assertEquals("fixture transition failure", assertThrows(IllegalStateException.class,
                () -> failed.core.consume(command(InventoryOrderSource.ORDER_CREATED, RESERVATION))).getMessage());
    }

    @Test void absentSourceCannotBeInterpretedAsCancellation() {
        var f = new Fixture();
        assertThrows(NullPointerException.class, () -> f.core.consume(command(null, RESERVATION)));
        assertTrue(f.rows.isEmpty()); assertFalse(f.calls.contains("release"));
    }

    private static InventoryOrderEventCommand command(InventoryOrderSource source, UUID reservation) {
        return new InventoryOrderEventCommand(EVENT, 101L, reservation, "order.created", source, "hash");
    }
    private static class Fixture implements InventoryReceiptPort, MenuItemInventoryUseCase, RestaurantTransactionPort {
        Map<UUID, Receipt> rows = new HashMap<>(); List<String> calls = new ArrayList<>(); boolean forceDuplicate, failTransition;
        DefaultInventoryOrderEventUseCase core = new DefaultInventoryOrderEventUseCase(this, this, this);
        public <T> T required(Supplier<T> work) { calls.add("transaction"); return work.get(); }
        public <T> T readOnly(Supplier<T> work) { throw new UnsupportedOperationException(); }
        public int insertIfAbsent(Receipt receipt) { calls.add("receipt"); if (forceDuplicate) return 0; return rows.putIfAbsent(receipt.eventId(), receipt) == null ? 1 : 0; }
        public Optional<Receipt> find(UUID id) { calls.add("find"); return Optional.ofNullable(rows.get(id)); }
        public InventoryReservationResult commit(UUID id, Long order) { calls.add("commit"); if (failTransition) throw new IllegalStateException("fixture transition failure"); return null; }
        public InventoryReservationResult release(UUID id, Long order) { calls.add("release"); return null; }
        public InventoryReservationResult reserve(InventoryReservationCommand command) { throw new UnsupportedOperationException(); }
        public int expireReservations() { throw new UnsupportedOperationException(); }
        public MenuItemInventoryResult getInventory(Long id) { throw new UnsupportedOperationException(); }
        public InventoryAvailability availability(Long restaurant, Long item, Integer quantity) { throw new UnsupportedOperationException(); }
        public MenuItemInventoryResult updateInventory(Long id, UpdateMenuItemInventoryCommand command) { throw new UnsupportedOperationException(); }
    }
}
