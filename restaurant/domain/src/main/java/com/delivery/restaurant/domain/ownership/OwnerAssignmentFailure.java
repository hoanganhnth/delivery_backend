package com.delivery.restaurant.domain.ownership;

public enum OwnerAssignmentFailure {
    ACTOR_NOT_ALLOWED,
    CANNOT_ASSIGN_ANOTHER_OWNER,
    OWNER_REQUIRED,
    OWNER_NOT_FOUND,
    OWNER_NOT_SHOP_OWNER,
    OWNER_NOT_ACTIVE
}
