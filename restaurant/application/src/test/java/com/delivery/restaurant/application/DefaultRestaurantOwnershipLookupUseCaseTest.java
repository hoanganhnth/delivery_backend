package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.*;
import com.delivery.restaurant.domain.ownership.*;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultRestaurantOwnershipLookupUseCaseTest {
    @Test void oldInternalClientsKeepCreatorLookupEvenWithEnforcement() {
        var f = new Fixture(new RestaurantManagementFacts(100L, 200L));
        assertEquals(new RestaurantOwnershipLookupResult(true, false), f.core.internalCheck(1L, 200L, null, true));
        assertFalse(f.core.internalCheck(1L, 100L, null, false).owned()); assertEquals(2, f.reads);
    }
    @Test void principalAwareInternalLookupNeverFallsBackOnMigratedRows() {
        var f = new Fixture(new RestaurantManagementFacts(100L, 200L));
        assertEquals(new RestaurantOwnershipLookupResult(true, false), f.core.internalCheck(1L, 100L, 999L, false));
        assertEquals(new RestaurantOwnershipLookupResult(false, false), f.core.internalCheck(1L, 101L, 200L, false));
        assertEquals(new RestaurantOwnershipLookupResult(true, false), f.core.internalCheck(1L, 100L, 200L, true));
    }
    @Test void internalFallbackCountsOnlyActualUnmigratedSuccessWhenFlagAllowsIt() {
        var f = new Fixture(new RestaurantManagementFacts(null, 200L));
        assertEquals(new RestaurantOwnershipLookupResult(true, true), f.core.internalCheck(1L, 100L, 200L, false));
        assertEquals(new RestaurantOwnershipLookupResult(false, false), f.core.internalCheck(1L, 100L, 200L, true));
        assertEquals(new RestaurantOwnershipLookupResult(false, false), f.core.internalCheck(1L, 100L, 201L, false));
    }
    @Test void orderDecisionUsesPrincipalOwnershipRatherThanCreatorOrCallerLegacyIdentity() {
        var f = new Fixture(new RestaurantManagementFacts(100L, 200L));
        assertTrue(f.core.canDecideOrder(1L, RestaurantActorRole.SHOP_OWNER, 100L, 999L, true));
        assertFalse(f.core.canDecideOrder(1L, RestaurantActorRole.SHOP_OWNER, 101L, 200L, false));
        assertFalse(f.core.canDecideOrder(1L, RestaurantActorRole.SHOP_OWNER, 200L, 200L, false));
    }
    @Test void orderDecisionLegacyFallbackIsRestrictedToUnmigratedRowsAndEnforcementOff() {
        var f = new Fixture(new RestaurantManagementFacts(null, 200L));
        assertTrue(f.core.canDecideOrder(1L, RestaurantActorRole.SHOP_OWNER, 100L, 200L, false));
        assertFalse(f.core.canDecideOrder(1L, RestaurantActorRole.SHOP_OWNER, 100L, 200L, true));
        assertFalse(f.core.canDecideOrder(1L, RestaurantActorRole.SHOP_OWNER, 100L, 201L, false));
    }
    @Test void adminHasExistingBypassButMissingPrincipalAndOtherRolesFailBeforeReads() {
        var f = new Fixture(null);
        assertTrue(f.core.canDecideOrder(1L, RestaurantActorRole.ADMIN, 100L, 200L, true));
        assertFalse(f.core.canDecideOrder(1L, RestaurantActorRole.ADMIN, null, 200L, true));
        assertFalse(f.core.canDecideOrder(1L, RestaurantActorRole.OTHER, 100L, 200L, false));
        assertFalse(f.core.canDecideOrder(1L, null, 100L, 200L, false)); assertEquals(0, f.reads);
    }
    @Test void missingRestaurantIsFalseAndEveryLookupUsesReadOnlyCoreTransaction() {
        var f = new Fixture(null);
        assertFalse(f.core.internalCheck(1L, 100L, 200L, false).owned());
        assertFalse(f.core.canDecideOrder(1L, RestaurantActorRole.SHOP_OWNER, 100L, 200L, false)); assertEquals(2, f.reads);
    }
    private static final class Fixture implements RestaurantOwnershipReadPort, RestaurantTransactionPort {
        final RestaurantManagementFacts facts; int reads; boolean active;
        final DefaultRestaurantOwnershipLookupUseCase core = new DefaultRestaurantOwnershipLookupUseCase(this, this, new DefaultRestaurantManagementAccessUseCase());
        Fixture(RestaurantManagementFacts facts) { this.facts = facts; }
        @Override public Optional<RestaurantManagementFacts> findOwnership(Long id) { assertTrue(active); return Optional.ofNullable(facts); }
        @Override public <T> T readOnly(Supplier<T> operation) { reads++; active = true; try { return operation.get(); } finally { active = false; } }
        @Override public <T> T required(Supplier<T> operation) { throw new UnsupportedOperationException(); }
        @Override public <T> T repeatableRead(Supplier<T> operation) { throw new UnsupportedOperationException(); }
    }
}
