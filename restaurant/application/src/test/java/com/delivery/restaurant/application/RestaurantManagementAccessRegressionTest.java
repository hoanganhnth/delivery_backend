package com.delivery.restaurant.application;

import com.delivery.restaurant.domain.ownership.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Retains the former ownership adapter vectors against the production core. */
class RestaurantManagementAccessRegressionTest {
    private final DefaultRestaurantManagementAccessUseCase access = new DefaultRestaurantManagementAccessUseCase();
    @Test void roleWithoutPrincipalIsNotAnAuthenticatedManager() {
        for (var role : new RestaurantActorRole[] {RestaurantActorRole.ADMIN, RestaurantActorRole.SHOP_OWNER})
            assertThrows(ManagementAccessException.class, () -> access.resolve(facts(7L, null), null, 7L, role, false));
    }
    @Test void principalOwnedRestaurantRejectsDifferentPrincipalEvenWhenLegacyIdMatches() {
        assertThrows(ManagementAccessException.class, () -> access.resolve(facts(101L, 7L), 99L, 101L, RestaurantActorRole.SHOP_OWNER, false));
    }
    @Test void principalOwnerCanManageWithoutUsingLegacyFallback() {
        assertFalse(access.resolve(facts(101L, 7L), 7L, 999L, RestaurantActorRole.SHOP_OWNER, false).usedLegacyFallback());
    }
    @Test void unmigratedRestaurantAllowsMatchingLegacyOwnerOnlyWhenEnforcementIsDisabled() {
        assertTrue(access.resolve(facts(101L, null), 7L, 101L, RestaurantActorRole.SHOP_OWNER, false).usedLegacyFallback());
    }
    @Test void unmigratedRestaurantRejectsLegacyOwnerWhenEnforcementIsEnabled() {
        assertThrows(ManagementAccessException.class, () -> access.resolve(facts(101L, null), 7L, 101L, RestaurantActorRole.SHOP_OWNER, true));
    }
    @Test void adminCanManageAnyRestaurantWithoutLegacyFallback() {
        assertFalse(access.resolve(facts(101L, 7L), 99L, 999L, RestaurantActorRole.ADMIN, true).usedLegacyFallback());
    }
    private RestaurantManagementFacts facts(Long creator, Long owner) { return new RestaurantManagementFacts(owner, creator); }
}
