package com.delivery.restaurant_service.service.ownership;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.entity.Restaurant;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestaurantOwnershipPolicyTest {
    @Test
    void roleWithoutPrincipalIsNotAnAuthenticatedManager() {
        for (String role : java.util.List.of(RoleConstants.ADMIN, RoleConstants.OWNER)) {
            assertThrows(AccessDeniedException.class, () -> new RestaurantOwnershipPolicy(false)
                    .assertCanManage(restaurant(7L, null), null, 7L, role));
        }
    }

    @Test
    void principalOwnedRestaurantRejectsDifferentPrincipalEvenWhenLegacyIdMatches() {
        Restaurant restaurant = restaurant(101L, 7L);
        RestaurantOwnershipPolicy policy = new RestaurantOwnershipPolicy(false);

        assertThrows(AccessDeniedException.class, () -> policy.assertCanManage(
                restaurant, 99L, 101L, RoleConstants.OWNER));
    }

    @Test
    void principalOwnerCanManageWithoutUsingLegacyFallback() {
        Restaurant restaurant = restaurant(101L, 7L);
        RestaurantOwnershipPolicy policy = new RestaurantOwnershipPolicy(false);

        ManagementAccess access = policy.assertCanManage(
                restaurant, 7L, 999L, RoleConstants.OWNER);

        assertFalse(access.usedLegacyFallback());
    }

    @Test
    void unmigratedRestaurantAllowsMatchingLegacyOwnerOnlyWhenEnforcementIsDisabled() {
        Restaurant restaurant = restaurant(101L, null);
        RestaurantOwnershipPolicy policy = new RestaurantOwnershipPolicy(false);

        ManagementAccess access = policy.assertCanManage(
                restaurant, 7L, 101L, RoleConstants.OWNER);

        assertTrue(access.usedLegacyFallback());
    }

    @Test
    void unmigratedRestaurantRejectsLegacyOwnerWhenEnforcementIsEnabled() {
        Restaurant restaurant = restaurant(101L, null);
        RestaurantOwnershipPolicy policy = new RestaurantOwnershipPolicy(true);

        assertThrows(AccessDeniedException.class, () -> policy.assertCanManage(
                restaurant, 7L, 101L, RoleConstants.OWNER));
    }

    @Test
    void adminCanManageAnyRestaurantWithoutLegacyFallback() {
        Restaurant restaurant = restaurant(101L, 7L);
        RestaurantOwnershipPolicy policy = new RestaurantOwnershipPolicy(true);

        ManagementAccess access = policy.assertCanManage(
                restaurant, 99L, 999L, RoleConstants.ADMIN);

        assertFalse(access.usedLegacyFallback());
    }

    private Restaurant restaurant(Long legacyCreatorId, Long ownerPrincipalId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setCreatorId(legacyCreatorId);
        restaurant.setOwnerPrincipalId(ownerPrincipalId);
        return restaurant;
    }
}
