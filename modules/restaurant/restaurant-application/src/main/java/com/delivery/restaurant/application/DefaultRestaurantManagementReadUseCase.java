package com.delivery.restaurant.application;

import com.delivery.restaurant.application.api.RestaurantManagementQuery;
import com.delivery.restaurant.application.api.RestaurantManagementReadUseCase;
import com.delivery.restaurant.application.api.RestaurantManagementResult;
import com.delivery.restaurant.application.api.RestaurantReadPort;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.ManagementAccessFailure;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import java.util.Objects;

/** Validates management actor context before the infrastructure query is selected. */
public final class DefaultRestaurantManagementReadUseCase
        implements RestaurantManagementReadUseCase {

    private static final int MANAGEMENT_LIMIT = 100;
    private final RestaurantReadPort readPort;

    public DefaultRestaurantManagementReadUseCase(RestaurantReadPort readPort) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
    }

    @Override
    public RestaurantManagementResult readForAdmin(Long principalId, Long legacyUserId,
            boolean principalOwnershipEnforced) {
        return read(principalId, legacyUserId, RestaurantActorRole.ADMIN,
                principalOwnershipEnforced);
    }

    @Override
    public RestaurantManagementResult readForOwner(Long principalId, Long legacyUserId,
            boolean principalOwnershipEnforced) {
        return read(principalId, legacyUserId, RestaurantActorRole.SHOP_OWNER,
                principalOwnershipEnforced);
    }

    private RestaurantManagementResult read(Long principalId, Long legacyUserId,
            RestaurantActorRole role, boolean principalOwnershipEnforced) {
        if (principalId == null || principalId <= 0 || legacyUserId == null || legacyUserId <= 0) {
            throw new ManagementAccessException(ManagementAccessFailure.PRINCIPAL_REQUIRED);
        }
        return readPort.findManagement(new RestaurantManagementQuery(
                principalId, legacyUserId, role, principalOwnershipEnforced, MANAGEMENT_LIMIT));
    }
}
