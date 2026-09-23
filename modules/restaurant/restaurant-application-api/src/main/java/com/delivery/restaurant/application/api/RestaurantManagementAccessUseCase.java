package com.delivery.restaurant.application.api;

import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.ownership.RestaurantManagementAccessDecision;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;

public interface RestaurantManagementAccessUseCase {
    RestaurantManagementAccessDecision resolve(
            RestaurantManagementFacts facts,
            Long principalId,
            Long legacyUserId,
            RestaurantActorRole actorRole,
            boolean principalOwnershipEnforced);
}
