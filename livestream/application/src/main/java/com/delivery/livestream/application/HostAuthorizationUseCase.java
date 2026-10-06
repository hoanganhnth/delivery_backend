package com.delivery.livestream.application;

import com.delivery.livestream.api.RestaurantOwnershipPort;
import com.delivery.livestream.domain.LivestreamPolicy;

public final class HostAuthorizationUseCase {
    private final RestaurantOwnershipPort ownership;

    public HostAuthorizationUseCase(RestaurantOwnershipPort ownership) {
        this.ownership = ownership;
    }

    public void requireHost(Long user, boolean admin, boolean shopOwner,
                            Long restaurant, Long principal, Long legacyUser) {
        if (LivestreamPolicy.hostRequiresOwnership(user, admin, shopOwner)) {
            ownership.requireOwnedBy(restaurant, principal, legacyUser);
        }
    }
}
