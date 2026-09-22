package com.delivery.restaurant.domain.catalog;

public final class MenuItemLifecyclePolicy {

    public MenuItemStatus transition(
            MenuItemStatus current,
            MenuItemStatus target,
            CatalogActorRole actorRole) {
        if (current == null || target == null) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.INVALID_LIFECYCLE_TRANSITION,
                    "Current and target Menu states are required");
        }
        if (actorRole == null) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.ACTOR_ROLE_REQUIRED,
                    "Actor role is required");
        }
        if (current == target) {
            return current;
        }
        if (current == MenuItemStatus.ARCHIVED) {
            if (target != MenuItemStatus.DISCONTINUED) {
                throw new CatalogDomainException(
                        CatalogRuleViolation.INVALID_LIFECYCLE_TRANSITION,
                        "Archived Menu item can only be restored to DISCONTINUED");
            }
            requireAdmin(actorRole);
            return MenuItemStatus.DISCONTINUED;
        }
        return target;
    }

    private void requireAdmin(CatalogActorRole actorRole) {
        if (actorRole != CatalogActorRole.ADMIN) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.ADMIN_REQUIRED,
                    "Only ADMIN can restore an archived Menu item");
        }
    }
}
