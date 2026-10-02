package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantActorRole;

/** Bounded management query with trusted identity facts supplied by the host. */
public record RestaurantManagementQuery(
        Long principalId,
        Long legacyUserId,
        RestaurantActorRole actorRole,
        boolean principalOwnershipEnforced,
        int limit) {}
