package com.delivery.restaurant.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MenuItemLifecyclePolicyTest {

    private final MenuItemLifecyclePolicy policy = new MenuItemLifecyclePolicy();

    @Test
    void everyMenuTransitionFollowsTheLifecycleMatrix() {
        for (MenuItemStatus current : MenuItemStatus.values()) {
            for (MenuItemStatus target : MenuItemStatus.values()) {
                for (CatalogActorRole role : CatalogActorRole.values()) {
                    String context = current + " -> " + target + " as " + role;
                    if (current == target || current != MenuItemStatus.ARCHIVED) {
                        assertEquals(target, policy.transition(current, target, role), context);
                    } else if (target != MenuItemStatus.DISCONTINUED) {
                        assertViolation(CatalogRuleViolation.INVALID_LIFECYCLE_TRANSITION,
                                () -> policy.transition(current, target, role), context);
                    } else if (role == CatalogActorRole.ADMIN) {
                        assertEquals(MenuItemStatus.DISCONTINUED,
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
                        () -> policy.transition(null, MenuItemStatus.AVAILABLE, CatalogActorRole.ADMIN)).violation());
        assertEquals(CatalogRuleViolation.INVALID_LIFECYCLE_TRANSITION,
                assertThrows(CatalogDomainException.class,
                        () -> policy.transition(MenuItemStatus.AVAILABLE, null, CatalogActorRole.ADMIN)).violation());
        assertEquals(CatalogRuleViolation.ACTOR_ROLE_REQUIRED,
                assertThrows(CatalogDomainException.class,
                        () -> policy.transition(MenuItemStatus.AVAILABLE, MenuItemStatus.SOLD_OUT, null)).violation());
    }

    private static void assertViolation(
            CatalogRuleViolation expected,
            org.junit.jupiter.api.function.Executable executable,
            String context) {
        CatalogDomainException exception = assertThrows(CatalogDomainException.class, executable, context);
        assertEquals(expected, exception.violation(), context);
    }
}
