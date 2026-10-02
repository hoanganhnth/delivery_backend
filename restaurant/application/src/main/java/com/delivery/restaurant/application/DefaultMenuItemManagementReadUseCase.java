package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.MenuItemManagementQuery;
import com.delivery.restaurant.application.api.MenuItemManagementReadUseCase;
import com.delivery.restaurant.application.api.MenuItemPageSlice;
import com.delivery.restaurant.application.api.MenuItemReadPort;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.ManagementAccessFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.util.Objects;
import java.util.Optional;

/** Validates management query bounds and delegates ownership/status filtering to the port. */
public final class DefaultMenuItemManagementReadUseCase
        implements MenuItemManagementReadUseCase {
    private static final int MANAGEMENT_LIMIT = 100;

    private final MenuItemReadPort readPort;

    public DefaultMenuItemManagementReadUseCase(MenuItemReadPort readPort) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
    }

    @Override
    public Optional<MenuItemPageSlice> read(MenuItemManagementQuery query) {
        Objects.requireNonNull(query, "query");
        requireIdentity(query);
        if (query.page() < 0 || query.size() < 1 || query.size() > MANAGEMENT_LIMIT) {
            throw new IllegalArgumentException("Invalid page or size");
        }
        return readPort.findManaged(query);
    }

    private void requireIdentity(MenuItemManagementQuery query) {
        if (query.principalId() == null || query.principalId() <= 0
                || query.legacyUserId() == null || query.legacyUserId() <= 0) {
            throw new ManagementAccessException(ManagementAccessFailure.PRINCIPAL_REQUIRED);
        }
        if (query.actorRole() != RestaurantActorRole.ADMIN
                && query.actorRole() != RestaurantActorRole.SHOP_OWNER) {
            throw new ManagementAccessException(ManagementAccessFailure.ACTOR_NOT_ALLOWED);
        }
    }
}
