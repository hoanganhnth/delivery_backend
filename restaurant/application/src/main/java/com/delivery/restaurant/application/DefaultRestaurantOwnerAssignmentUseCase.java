package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.PrincipalOwnershipDirectory;
import com.delivery.restaurant.application.api.RestaurantOwnerAssignmentUseCase;
import com.delivery.restaurant.domain.ownership.PrincipalOwnershipFacts;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentException;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.util.Objects;

public final class DefaultRestaurantOwnerAssignmentUseCase
        implements RestaurantOwnerAssignmentUseCase {

    private final PrincipalOwnershipDirectory principalDirectory;

    public DefaultRestaurantOwnerAssignmentUseCase(
            PrincipalOwnershipDirectory principalDirectory) {
        this.principalDirectory = Objects.requireNonNull(principalDirectory, "principalDirectory");
    }

    @Override
    public long resolveOwnerPrincipalId(
            long actorPrincipalId,
            RestaurantActorRole actorRole,
            Long requestedOwnerPrincipalId) {
        requirePositive(actorPrincipalId, "actorPrincipalId");
        if (actorRole == RestaurantActorRole.SHOP_OWNER) {
            if (requestedOwnerPrincipalId != null
                    && requestedOwnerPrincipalId.longValue() != actorPrincipalId) {
                throw new OwnerAssignmentException(
                        OwnerAssignmentFailure.CANNOT_ASSIGN_ANOTHER_OWNER);
            }
            return actorPrincipalId;
        }
        if (actorRole != RestaurantActorRole.ADMIN) {
            throw new OwnerAssignmentException(OwnerAssignmentFailure.ACTOR_NOT_ALLOWED);
        }
        if (requestedOwnerPrincipalId == null) {
            throw new OwnerAssignmentException(OwnerAssignmentFailure.OWNER_REQUIRED);
        }
        requirePositive(requestedOwnerPrincipalId, "requestedOwnerPrincipalId");
        PrincipalOwnershipFacts owner = principalDirectory
                .findByPrincipalId(requestedOwnerPrincipalId)
                .filter(candidate -> candidate.principalId() == requestedOwnerPrincipalId)
                .orElseThrow(() -> new OwnerAssignmentException(
                        OwnerAssignmentFailure.OWNER_NOT_FOUND));
        if (!owner.shopOwner()) {
            throw new OwnerAssignmentException(OwnerAssignmentFailure.OWNER_NOT_SHOP_OWNER);
        }
        if (!owner.active()) {
            throw new OwnerAssignmentException(OwnerAssignmentFailure.OWNER_NOT_ACTIVE);
        }
        return owner.principalId();
    }

    private void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
