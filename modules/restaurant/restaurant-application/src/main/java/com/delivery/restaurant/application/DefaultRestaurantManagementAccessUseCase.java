package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.ManagementAccessFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import com.delivery.restaurant.domain.ownership.RestaurantManagementAccessDecision;

public final class DefaultRestaurantManagementAccessUseCase
        implements RestaurantManagementAccessUseCase {

    @Override
    public RestaurantManagementAccessDecision resolve(
            RestaurantManagementFacts facts,
            Long principalId,
            Long legacyUserId,
            RestaurantActorRole actorRole,
            boolean principalOwnershipEnforced) {
        if (facts == null) {
            throw new IllegalArgumentException("facts are required");
        }
        if (principalId == null || principalId <= 0) {
            throw new ManagementAccessException(ManagementAccessFailure.PRINCIPAL_REQUIRED);
        }
        if (actorRole == RestaurantActorRole.ADMIN) {
            return RestaurantManagementAccessDecision.direct();
        }
        if (actorRole != RestaurantActorRole.SHOP_OWNER) {
            throw new ManagementAccessException(ManagementAccessFailure.ACTOR_NOT_ALLOWED);
        }
        if (facts.ownerPrincipalId() != null && facts.ownerPrincipalId().equals(principalId)) {
            return RestaurantManagementAccessDecision.direct();
        }
        if (facts.ownerPrincipalId() == null
                && !principalOwnershipEnforced
                && legacyUserId != null
                && legacyUserId.equals(facts.creatorId())) {
            return RestaurantManagementAccessDecision.legacyFallback();
        }
        throw new ManagementAccessException(ManagementAccessFailure.ACTOR_DOES_NOT_OWN_RESTAURANT);
    }
}
