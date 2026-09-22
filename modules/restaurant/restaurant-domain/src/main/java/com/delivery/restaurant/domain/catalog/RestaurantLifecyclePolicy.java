package com.delivery.restaurant.domain.catalog;

public final class RestaurantLifecyclePolicy {

    public RestaurantStatus initialStatus() {
        return RestaurantStatus.ACTIVE;
    }

    public RestaurantStatus transition(
            RestaurantStatus current,
            RestaurantStatus target,
            CatalogActorRole actorRole) {
        if (current == null || target == null) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.INVALID_LIFECYCLE_TRANSITION,
                    "Current and target Restaurant states are required");
        }
        if (actorRole == null) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.ACTOR_ROLE_REQUIRED,
                    "Actor role is required");
        }
        if (current == target) {
            return current;
        }
        if (current == RestaurantStatus.ARCHIVED) {
            if (target != RestaurantStatus.PAUSED) {
                throw new CatalogDomainException(
                        CatalogRuleViolation.INVALID_LIFECYCLE_TRANSITION,
                        "Archived Restaurant can only be restored to PAUSED");
            }
            requireAdmin(actorRole);
            return RestaurantStatus.PAUSED;
        }
        return target;
    }

    private void requireAdmin(CatalogActorRole actorRole) {
        if (actorRole != CatalogActorRole.ADMIN) {
            throw new CatalogDomainException(
                    CatalogRuleViolation.ADMIN_REQUIRED,
                    "Only ADMIN can restore an archived Restaurant");
        }
    }
}
