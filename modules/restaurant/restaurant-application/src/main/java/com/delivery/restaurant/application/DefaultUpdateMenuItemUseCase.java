package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.MenuItemUpdateResult;
import com.delivery.restaurant.application.api.MenuItemUpdatePort;
import com.delivery.restaurant.application.api.MenuItemMutationPlan;
import com.delivery.restaurant.application.api.MenuItemStoredFacts;
import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant.application.api.UpdateMenuItemCommand;
import com.delivery.restaurant.application.api.UpdateMenuItemUseCase;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.ManagementAccessFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.ownership.RestaurantManagementAccessDecision;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import java.util.Objects;
import java.util.Optional;

/** Performs Menu update authorization and planning before the persistence adapter runs. */
public final class DefaultUpdateMenuItemUseCase implements UpdateMenuItemUseCase {
    private final MenuItemUpdatePort updatePort;
    private final RestaurantManagementAccessUseCase accessUseCase;

    public DefaultUpdateMenuItemUseCase(
            MenuItemUpdatePort updatePort,
            RestaurantManagementAccessUseCase accessUseCase) {
        this.updatePort = Objects.requireNonNull(updatePort, "updatePort");
        this.accessUseCase = Objects.requireNonNull(accessUseCase, "accessUseCase");
    }

    @Override
    public Optional<MenuItemUpdateResult> update(UpdateMenuItemCommand command) {
        Objects.requireNonNull(command, "command");
        requireCommandIdentity(command);
        return updatePort.update(command, storedFacts -> plan(command, storedFacts));
    }

    private MenuItemMutationPlan plan(UpdateMenuItemCommand command,
            MenuItemStoredFacts storedFacts) {
        Objects.requireNonNull(storedFacts, "storedFacts");
        RestaurantManagementAccessDecision access = accessUseCase.resolve(
                new RestaurantManagementFacts(
                        storedFacts.restaurantOwnerPrincipalId(),
                        storedFacts.restaurantCreatorId()),
                command.actorPrincipalId(),
                command.legacyUserId(),
                command.actorRole(),
                command.principalOwnershipEnforced());

        Long ownerPrincipalId = access.usedLegacyFallback()
                ? command.actorPrincipalId() : storedFacts.restaurantOwnerPrincipalId();
        return new MenuItemMutationPlan(
                storedFacts.menuItemId(),
                storedFacts.restaurantId(),
                ownerPrincipalId,
                command.name() != null ? command.name() : storedFacts.name(),
                command.description() != null ? command.description() : storedFacts.description(),
                command.price() != null ? command.price() : storedFacts.price(),
                command.status() != null ? command.status() : storedFacts.status(),
                command.image() != null ? command.image() : storedFacts.image());
    }

    private void requireCommandIdentity(UpdateMenuItemCommand command) {
        if (command.menuItemId() == null || command.menuItemId() <= 0
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
