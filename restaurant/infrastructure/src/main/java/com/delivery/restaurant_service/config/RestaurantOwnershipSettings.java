package com.delivery.restaurant_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Deployment flag only; authorization decisions belong to the application core. */
@Component
public class RestaurantOwnershipSettings {
    private final boolean principalOwnershipEnforced;
    public RestaurantOwnershipSettings(
            @Value("${app.identity.principal-ownership.enforced:false}") boolean principalOwnershipEnforced) {
        this.principalOwnershipEnforced = principalOwnershipEnforced;
    }
    public boolean isPrincipalOwnershipEnforced() { return principalOwnershipEnforced; }
}
