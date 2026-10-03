package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultLivestreamProductUseCaseTest {
    @Test void returnsCanonicalMetadataInsideReadOnlyTransaction() {
        var f = new Fixture(42L, MenuItemStatus.AVAILABLE);
        assertEquals(f.item, f.core.findAvailable(42L, 10L).orElseThrow());
        assertEquals(1, f.reads);
    }
    @Test void missingAndForeignOrParentlessProductsAreNotEligible() {
        var f = new Fixture(43L, MenuItemStatus.AVAILABLE);
        assertTrue(f.core.findAvailable(42L, 10L).isEmpty());
        f.item = new LivestreamProductSnapshot(10L, null, "canonical", "image", null, MenuItemStatus.AVAILABLE);
        assertTrue(f.core.findAvailable(42L, 10L).isEmpty());
        f.item = null;
        assertTrue(f.core.findAvailable(42L, 10L).isEmpty());
    }
    @Test void everyNonAvailableOrMissingStatusFailsClosed() {
        for (var status : MenuItemStatus.values()) {
            var f = new Fixture(42L, status);
            assertEquals(status == MenuItemStatus.AVAILABLE, f.core.findAvailable(42L, 10L).isPresent());
        }
        assertTrue(new Fixture(42L, null).core.findAvailable(42L, 10L).isEmpty());
    }
    @Test void invalidScopesNeverReachPersistence() {
        var f = new Fixture(42L, MenuItemStatus.AVAILABLE);
        for (Long invalid : new Long[]{null, 0L, -1L}) {
            assertThrows(IllegalArgumentException.class, () -> f.core.findAvailable(invalid, 10L));
            assertThrows(IllegalArgumentException.class, () -> f.core.findAvailable(42L, invalid));
        }
        assertEquals(0, f.reads);
    }
    private static final class Fixture implements LivestreamProductReadPort, RestaurantTransactionPort {
        LivestreamProductSnapshot item; int reads; boolean active;
        final DefaultLivestreamProductUseCase core = new DefaultLivestreamProductUseCase(this, this);
        Fixture(Long restaurant, MenuItemStatus status) {
            item = new LivestreamProductSnapshot(10L, restaurant, "canonical", "image", "restaurant", status);
        }
        public Optional<LivestreamProductSnapshot> findProduct(Long id) {
            assertTrue(active); assertEquals(10L, id); return Optional.ofNullable(item);
        }
        public <T> T readOnly(Supplier<T> work) {
            reads++; active = true; try { return work.get(); } finally { active = false; }
        }
        public <T> T required(Supplier<T> work) { throw new UnsupportedOperationException(); }
        public <T> T repeatableRead(Supplier<T> work) { throw new UnsupportedOperationException(); }
    }
}
