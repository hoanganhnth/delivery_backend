package com.delivery.restaurant.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RestaurantLifecyclePolicyTest {

    private final RestaurantLifecyclePolicy policy = new RestaurantLifecyclePolicy();

    @Test
    void newRestaurantStartsActive() {
        assertEquals(RestaurantStatus.ACTIVE, policy.initialStatus());
    }

    @Test
    void everyRestaurantTransitionFollowsTheLifecycleMatrix() {
        for (RestaurantStatus current : RestaurantStatus.values()) {
            for (RestaurantStatus target : RestaurantStatus.values()) {
                for (CatalogActorRole role : CatalogActorRole.values()) {
                    String context = current + " -> " + target + " as " + role;
                    if (current == target || current != RestaurantStatus.ARCHIVED) {
                        assertEquals(target, policy.transition(current, target, role), context);
                    } else if (target != RestaurantStatus.PAUSED) {
                        assertViolation(CatalogRuleViolation.INVALID_LIFECYCLE_TRANSITION,
                                () -> policy.transition(current, target, role), context);
                    } else if (role == CatalogActorRole.ADMIN) {
                        assertEquals(RestaurantStatus.PAUSED,
                                policy.transition(current, target, role), context);
                    } else {
                        assertViolation(CatalogRuleViolation.ADMIN_REQUIRED,
                                () -> policy.transition(current, target, role), context);
                    }
                }
            }
        }
    }

    @Test
    void transitionRejectsMissingInputs() {
        assertEquals(CatalogRuleViolation.INVALID_LIFECYCLE_TRANSITION,
                assertThrows(CatalogDomainException.class,
                        () -> policy.transition(null, RestaurantStatus.ACTIVE, CatalogActorRole.ADMIN)).violation());
        assertEquals(CatalogRuleViolation.INVALID_LIFECYCLE_TRANSITION,
                assertThrows(CatalogDomainException.class,
                        () -> policy.transition(RestaurantStatus.ACTIVE, null, CatalogActorRole.ADMIN)).violation());
        assertEquals(CatalogRuleViolation.ACTOR_ROLE_REQUIRED,
                assertThrows(CatalogDomainException.class,
                        () -> policy.transition(RestaurantStatus.ACTIVE, RestaurantStatus.PAUSED, null)).violation());
    }

    private static void assertViolation(
            CatalogRuleViolation expected,
            org.junit.jupiter.api.function.Executable executable,
            String context) {
        CatalogDomainException exception = assertThrows(CatalogDomainException.class, executable, context);
        assertEquals(expected, exception.violation(), context);
    }
}
