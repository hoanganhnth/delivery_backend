package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.CreateRestaurantCommand;
import com.delivery.restaurant.application.api.CreateRestaurantResult;
import com.delivery.restaurant.application.api.CreateRestaurantUseCase;
import com.delivery.restaurant.application.api.RestaurantCreationPort;
import com.delivery.restaurant.application.api.RestaurantOwnerAssignmentUseCase;
import com.delivery.restaurant.domain.catalog.OperatingSchedule;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentException;
import com.delivery.restaurant.domain.ownership.OwnerAssignmentFailure;
import java.time.ZoneId;
import java.util.Objects;

public final class DefaultCreateRestaurantUseCase implements CreateRestaurantUseCase {
    private final RestaurantOwnerAssignmentUseCase ownerAssignment;
    private final RestaurantCreationPort creationPort;

    public DefaultCreateRestaurantUseCase(RestaurantOwnerAssignmentUseCase ownerAssignment,
            RestaurantCreationPort creationPort) {
        this.ownerAssignment = Objects.requireNonNull(ownerAssignment, "ownerAssignment");
        this.creationPort = Objects.requireNonNull(creationPort, "creationPort");
    }

    @Override
    public CreateRestaurantResult create(CreateRestaurantCommand command) {
        if (command.actorPrincipalId() == null || command.creatorId() == null) {
            throw new OwnerAssignmentException(OwnerAssignmentFailure.ACTOR_NOT_ALLOWED);
        }
        long ownerPrincipalId = ownerAssignment.resolveOwnerPrincipalId(
                command.actorPrincipalId(), command.actorRole(), command.requestedOwnerPrincipalId());
        // Creation has no timezone override: preserve the existing persistence default.
        OperatingSchedule.of(command.openingHour(), command.closingHour(), ZoneId.of("Asia/Ho_Chi_Minh"));
        return creationPort.create(command, ownerPrincipalId);
    }
}
