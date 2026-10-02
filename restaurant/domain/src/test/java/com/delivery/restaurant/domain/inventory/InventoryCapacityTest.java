package com.delivery.restaurant.domain.inventory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryCapacityTest {
    @Test void reservationCommitAndCompensationPreserveStockAndAdvanceRevision() {
        var original = new InventoryCapacity(5, 0, 0L);
        var held = original.reserve(2, 11L);
        assertEquals(new InventoryCapacity(5, 2, 1L), held);
        assertEquals(3, held.availableQuantity());
        var sold = held.commit(2);
        assertEquals(new InventoryCapacity(3, 0, 2L), sold);
        assertEquals(new InventoryCapacity(5, 0, 3L), sold.restoreCommitted(2));
        assertEquals(new InventoryCapacity(5, 0, 2L), held.release(2));
        assertEquals(new InventoryCapacity(5, 0, 0L), original);
    }

    @Test void exhaustedMissingAndInconsistentLedgerCannotGrantCapacity() {
        for (InventoryCapacity capacity : new InventoryCapacity[]{new InventoryCapacity(5, 5, 0L),
                new InventoryCapacity(5, 6, 0L), new InventoryCapacity(null, 0, 0L),
                new InventoryCapacity(5, null, 0L), new InventoryCapacity(5, 4, 0L)}) {
            assertFalse(capacity.canReserve(2));
            assertEquals("Insufficient inventory for menu item 11", assertThrows(IllegalArgumentException.class,
                    () -> capacity.reserve(2, 11L)).getMessage());
        }
        assertEquals(0, new InventoryCapacity(5, 6, 0L).availableQuantity());
        assertTrue(new InventoryCapacity(5, 3, 0L).canReserve(2));
    }

    @Test void commitAndReleaseRejectInconsistentLedgerBeforeChangingIt() {
        var missingHold = new InventoryCapacity(5, 1, 0L);
        assertThrows(IllegalStateException.class, () -> missingHold.commit(2));
        assertThrows(IllegalStateException.class, () -> missingHold.release(2));
        var missingStock = new InventoryCapacity(1, 2, 0L);
        assertThrows(IllegalStateException.class, () -> missingStock.commit(2));
        assertEquals(new InventoryCapacity(5, 1, 0L), missingHold);
    }

    @Test void stockEditsRequireCurrentRevisionAndCannotInvalidateHolds() {
        var held = new InventoryCapacity(5, 2, 4L);
        assertThrows(IllegalArgumentException.class, () -> held.updateOnHand(8, null));
        assertThrows(IllegalArgumentException.class, () -> held.updateOnHand(8, 3L));
        assertThrows(IllegalArgumentException.class, () -> held.updateOnHand(1, 4L));
        assertEquals(new InventoryCapacity(8, 2, 5L), held.updateOnHand(8, 4L));
        assertEquals(new InventoryCapacity(2, 2, 5L), held.updateOnHand(2, 4L));
    }

    @Test void integerAndRevisionOverflowNeverProduceWrappedCounters() {
        assertThrows(ArithmeticException.class, () -> new InventoryCapacity(Integer.MAX_VALUE, 0, 0L).restoreCommitted(1));
        var exhaustedRevision = new InventoryCapacity(5, 2, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> exhaustedRevision.reserve(1, 11L));
        assertThrows(ArithmeticException.class, () -> exhaustedRevision.commit(1));
        assertThrows(ArithmeticException.class, () -> exhaustedRevision.release(1));
        assertThrows(ArithmeticException.class, () -> exhaustedRevision.restoreCommitted(1));
        assertThrows(ArithmeticException.class, () -> exhaustedRevision.updateOnHand(8, Long.MAX_VALUE));
        assertEquals(new InventoryCapacity(5, 2, Long.MAX_VALUE), exhaustedRevision);
    }
}
