package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.CreateMenuItemCommand;
import com.delivery.restaurant.application.api.CreateMenuItemUseCase;
import com.delivery.restaurant.application.api.MenuItemCreateDecision;
import com.delivery.restaurant.application.api.MenuItemCreateResult;
import com.delivery.restaurant.application.api.MenuItemCreationPort;
import com.delivery.restaurant.application.api.MenuItemMutationPlan;
import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant.domain.catalog.MenuItemStatus;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.ManagementAccessFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.ownership.RestaurantManagementAccessDecision;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import java.util.Objects;
import java.util.Optional;

/** Performs Menu creation authorization and planning before the persistence adapter runs. */
public final class DefaultCreateMenuItemUseCase implements CreateMenuItemUseCase {
    private final MenuItemCreationPort creationPort;
    private final RestaurantManagementAccessUseCase accessUseCase;

    public DefaultCreateMenuItemUseCase(
            MenuItemCreationPort creationPort,
            RestaurantManagementAccessUseCase accessUseCase) {
        this.creationPort = Objects.requireNonNull(creationPort, "creationPort");
        this.accessUseCase = Objects.requireNonNull(accessUseCase, "accessUseCase");
    }

    @Override
    public Optional<MenuItemCreateResult> create(CreateMenuItemCommand command) {
        Objects.requireNonNull(command, "command");
        requireCommandIdentity(command);
        MenuItemCreateDecision decision = facts -> plan(command, facts);
        return creationPort.create(command, decision);
    }

    private MenuItemMutationPlan plan(CreateMenuItemCommand command,
            RestaurantManagementFacts restaurantFacts) {
        Objects.requireNonNull(restaurantFacts, "restaurantFacts");
        RestaurantManagementAccessDecision access = accessUseCase.resolve(
                restaurantFacts,
                command.actorPrincipalId(),
                command.legacyUserId(),
                command.actorRole(),
                command.principalOwnershipEnforced());

        Long ownerPrincipalId = access.usedLegacyFallback()
                ? command.actorPrincipalId() : restaurantFacts.ownerPrincipalId();
        return new MenuItemMutationPlan(
                null,
                command.restaurantId(),
                ownerPrincipalId,
                command.name(),
                command.description(),
                command.price(),
                MenuItemStatus.AVAILABLE,
                command.image());
    }

    private void requireCommandIdentity(CreateMenuItemCommand command) {
        if (command.restaurantId() == null || command.restaurantId() <= 0
                || command.actorPrincipalId() == null || command.actorPrincipalId() <= 0
                || command.legacyUserId() == null || command.legacyUserId() <= 0) {
            throw new ManagementAccessException(ManagementAccessFailure.PRINCIPAL_REQUIRED);
        }
        if (command.actorRole() != RestaurantActorRole.ADMIN
                && command.actorRole() != RestaurantActorRole.SHOP_OWNER) {
            throw new ManagementAccessException(ManagementAccessFailure.ACTOR_NOT_ALLOWED);
        }
    }
}
