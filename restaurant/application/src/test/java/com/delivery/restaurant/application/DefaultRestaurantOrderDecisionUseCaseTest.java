package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.decision.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultRestaurantOrderDecisionUseCaseTest {
    @Test void firstConfirmationAndRejectionRequireOrderEligibilityAndPersistAtomically() {
        var f = new Fixture(); f.core().confirm(101L, 7L, 70L, 20, "Ready soon");
        assertThat(f.calls).containsExactly("begin", "fingerprint", "lock", "find", "eligible", "insert", "commit");
        assertThat(f.saved).isEqualTo(new RestaurantDecisionCommand(101L, 7L, 70L, RestaurantDecisionKind.CONFIRMED, 20, "Ready soon", null));
        f = new Fixture(); f.core().reject(101L, 7L, 70L, "Closed");
        assertThat(f.saved).isEqualTo(new RestaurantDecisionCommand(101L, 7L, 70L, RestaurantDecisionKind.REJECTED, null, null, "Closed"));
    }

    @Test void storedFingerprintMakesExactReplayIdempotentWithoutRemoteEligibility() {
        var f = new Fixture(); f.stored = new RestaurantDecisionStorePort.StoredDecision(7L, RestaurantDecisionKind.CONFIRMED, "expected");
        f.core().confirm(101L, 7L, 70L, 20, null);
        assertThat(f.calls).containsExactly("begin", "fingerprint", "lock", "find", "commit");
    }

    @Test void oppositeRestaurantOrDecisionRejectsBeforeLegacyPayloadLookup() {
        for (var stored : List.of(new RestaurantDecisionStorePort.StoredDecision(8L, RestaurantDecisionKind.CONFIRMED, null),
                new RestaurantDecisionStorePort.StoredDecision(7L, RestaurantDecisionKind.REJECTED, null))) {
            var f = new Fixture(); f.stored = stored;
            assertThatThrownBy(() -> f.core().confirm(101L, 7L, 70L, 20, null))
                    .isInstanceOf(RestaurantDecisionConflictException.class).hasMessageContaining("already has decision");
            assertThat(f.calls).doesNotContain("legacy", "eligible", "insert");
        }
    }

    @Test void contradictoryStoredOrLegacyFingerprintRejectsAndMissingLegacyKeepsCompatibility() {
        var current = new Fixture(); current.stored = new RestaurantDecisionStorePort.StoredDecision(7L, RestaurantDecisionKind.CONFIRMED, "different");
        assertConflict(current);
        var legacy = new Fixture(); legacy.stored = new RestaurantDecisionStorePort.StoredDecision(7L, RestaurantDecisionKind.CONFIRMED, null);
        legacy.legacy = "different"; assertConflict(legacy);
        legacy.legacy = "expected"; legacy.core().confirm(101L, 7L, 70L, 20, null);
        legacy.legacy = null; legacy.core().confirm(101L, 7L, 70L, 20, null);
        assertThat(legacy.calls).doesNotContain("eligible", "insert");
    }

    @Test void deniedOrderDoesNotPersistDecision() {
        var f = new Fixture(); f.eligible = false;
        assertThatThrownBy(() -> f.core().reject(101L, 7L, 70L, "Closed")).hasMessage("Order is not pending");
        assertThat(f.saved).isNull();
    }

    @Test void identitiesPrepTimeAndRejectionReasonAreValidatedBeforePersistence() {
        var f = new Fixture(); var c = f.core();
        for (Long invalid : Arrays.asList(null, 0L, -1L)) {
            assertThatThrownBy(() -> c.confirm(invalid, 7L, 70L, 20, null)).hasMessage("orderId must be positive");
            assertThatThrownBy(() -> c.confirm(101L, invalid, 70L, 20, null)).hasMessage("restaurantId must be positive");
            assertThatThrownBy(() -> c.confirm(101L, 7L, invalid, 20, null)).hasMessage("actorUserId must be positive");
            assertThatThrownBy(() -> c.reject(invalid, 7L, 70L, "Closed")).hasMessage("orderId must be positive");
        }
        for (Integer invalid : Arrays.asList(null, 0, -1, 241))
            assertThatThrownBy(() -> c.confirm(101L, 7L, 70L, invalid, null)).hasMessage("estimatedPrepTime must be between 1 and 240");
        for (String invalid : Arrays.asList(null, "", " "))
            assertThatThrownBy(() -> c.reject(101L, 7L, 70L, invalid)).hasMessage("rejectionReason must not be blank");
        assertThat(f.calls).isEmpty();
    }

    private void assertConflict(Fixture f) {
        assertThatThrownBy(() -> f.core().confirm(101L, 7L, 70L, 20, null))
                .isInstanceOf(RestaurantDecisionConflictException.class)
                .hasMessage("Restaurant decision replay has a contradictory payload");
        assertThat(f.saved).isNull();
    }
    private static final class Fixture implements RestaurantDecisionStorePort, OrderDecisionEligibilityPort, RestaurantTransactionPort {
        final List<String> calls = new ArrayList<>();
        StoredDecision stored; RestaurantDecisionCommand saved; String legacy; boolean eligible = true;
        DefaultRestaurantOrderDecisionUseCase core() { return new DefaultRestaurantOrderDecisionUseCase(this, this, this); }
        public <T> T required(Supplier<T> action) { calls.add("begin"); T result = action.get(); calls.add("commit"); return result; }
        public void lockOrder(Long id) { calls.add("lock"); }
        public Optional<StoredDecision> find(Long id) { calls.add("find"); return Optional.ofNullable(stored); }
        public Optional<String> legacyFingerprint(Long id, RestaurantDecisionKind decision) { calls.add("legacy"); return Optional.ofNullable(legacy); }
        public String fingerprint(RestaurantDecisionCommand c) { calls.add("fingerprint"); return "expected"; }
        public void insertDecisionAndEvent(RestaurantDecisionCommand c, String fingerprint) { calls.add("insert"); saved = c; }
        public void requirePendingOrderForRestaurant(Long order, Long restaurant) {
            calls.add("eligible"); if (!eligible) throw new IllegalArgumentException("Order is not pending");
        }
    }
}
