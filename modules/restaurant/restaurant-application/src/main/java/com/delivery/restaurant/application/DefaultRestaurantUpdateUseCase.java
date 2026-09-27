package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant.application.api.RestaurantMutationPlan;
import com.delivery.restaurant.application.api.RestaurantSnapshot;
import com.delivery.restaurant.application.api.RestaurantStoredFacts;
import com.delivery.restaurant.application.api.RestaurantUpdatePort;
import com.delivery.restaurant.application.api.RestaurantUpdateResult;
import com.delivery.restaurant.application.api.UpdateRestaurantCommand;
import com.delivery.restaurant.application.api.UpdateRestaurantUseCase;
import com.delivery.restaurant.domain.catalog.OperatingSchedule;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.ManagementAccessFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.ownership.RestaurantManagementAccessDecision;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/** Performs all update decisions before the infrastructure adapter mutates an entity. */
public final class DefaultRestaurantUpdateUseCase implements UpdateRestaurantUseCase {

    private static final int UNUSED = 0;

    private final RestaurantUpdatePort updatePort;
    private final RestaurantManagementAccessUseCase accessUseCase;

    public DefaultRestaurantUpdateUseCase(
            RestaurantUpdatePort updatePort,
            RestaurantManagementAccessUseCase accessUseCase) {
        this.updatePort = Objects.requireNonNull(updatePort, "updatePort");
        this.accessUseCase = Objects.requireNonNull(accessUseCase, "accessUseCase");
    }

    @Override
    public Optional<RestaurantUpdateResult> update(UpdateRestaurantCommand command) {
        Objects.requireNonNull(command, "command");
        requireCommandIdentity(command);
        return updatePort.update(command, storedFacts -> plan(command, storedFacts));
    }

    private RestaurantMutationPlan plan(UpdateRestaurantCommand command,
            RestaurantStoredFacts stored) {
        Objects.requireNonNull(stored, "storedFacts");
        RestaurantManagementAccessDecision access = accessUseCase.resolve(
                new RestaurantManagementFacts(stored.ownerPrincipalId(), stored.creatorId()),
                command.actorPrincipalId(), command.legacyUserId(), command.actorRole(),
                command.principalOwnershipEnforced());

        Long ownerPrincipalId = access.usedLegacyFallback()
                ? command.actorPrincipalId() : stored.ownerPrincipalId();
        LocalTime openingHour = command.openingHour() != null
                ? command.openingHour() : stored.openingHour();
        LocalTime closingHour = command.closingHour() != null
                ? command.closingHour() : stored.closingHour();
        OperatingSchedule.of(openingHour, closingHour, ZoneId.of(stored.timeZone()));

        return new RestaurantMutationPlan(
                stored.restaurantId(),
                ownerPrincipalId,
                command.name() != null ? command.name() : stored.name(),
                command.address() != null ? command.address() : stored.address(),
                command.phone() != null ? command.phone() : stored.phone(),
                openingHour,
                closingHour,
                command.defaultPrepTimeMinutes() != null
                        ? command.defaultPrepTimeMinutes() : stored.defaultPrepTimeMinutes(),
                command.image() != null ? command.image() : stored.image(),
                command.description() != null ? command.description() : stored.description(),
                command.latitude() != null ? command.latitude() : stored.latitude(),
                command.longitude() != null ? command.longitude() : stored.longitude());
    }

    private void requireCommandIdentity(UpdateRestaurantCommand command) {
        if (command.restaurantId() == null || command.restaurantId() <= UNUSED
                || command.actorPrincipalId() == null || command.actorPrincipalId() <= UNUSED
                || command.legacyUserId() == null || command.legacyUserId() <= UNUSED) {
            throw new ManagementAccessException(ManagementAccessFailure.PRINCIPAL_REQUIRED);
        }
        if (command.actorRole() != RestaurantActorRole.ADMIN
                && command.actorRole() != RestaurantActorRole.SHOP_OWNER) {
            throw new ManagementAccessException(ManagementAccessFailure.ACTOR_NOT_ALLOWED);
        }
    }
}
