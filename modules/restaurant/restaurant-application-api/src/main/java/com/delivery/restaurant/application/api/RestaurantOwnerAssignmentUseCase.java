package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantActorRole;

public interface RestaurantOwnerAssignmentUseCase {

    long resolveOwnerPrincipalId(
            long actorPrincipalId,
            RestaurantActorRole actorRole,
            Long requestedOwnerPrincipalId);
}
