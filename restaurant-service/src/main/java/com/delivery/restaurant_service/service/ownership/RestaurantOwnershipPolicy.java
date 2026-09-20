package com.delivery.restaurant_service.service.ownership;

import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.entity.Restaurant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Decides whether an actor may manage a Restaurant and every resource owned by it.
 * Menu items derive ownership from their parent Restaurant.
 */
@Component
public class RestaurantOwnershipPolicy {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RestaurantOwnershipPolicy.class);
    private final boolean principalOwnershipEnforced;

    public RestaurantOwnershipPolicy(
            @Value("${app.identity.principal-ownership.enforced:false}") boolean principalOwnershipEnforced) {
        this.principalOwnershipEnforced = principalOwnershipEnforced;
    }

    public ManagementAccess assertCanManage(Restaurant restaurant, Long principalId,
                                             Long legacyUserId, String role) {
        if (principalId == null || principalId <= 0) throw denied();
        if (RoleConstants.ADMIN.equalsIgnoreCase(role)) {
            return ManagementAccess.direct();
        }
        if (!RoleConstants.OWNER.equalsIgnoreCase(role)) {
            throw denied();
        }

        if (restaurant.getOwnerPrincipalId() != null) {
            if (principalId != null && principalId.equals(restaurant.getOwnerPrincipalId())) {
                return ManagementAccess.direct();
            }
            throw denied();
        }

        if (principalOwnershipEnforced || legacyUserId == null
                || !legacyUserId.equals(restaurant.getCreatorId())) {
            throw denied();
        }
        log.info("Restaurant management used legacy ownership fallback");
        return ManagementAccess.legacyFallback();
    }

    public boolean isPrincipalOwnershipEnforced() {
        return principalOwnershipEnforced;
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Actor does not own this restaurant");
    }
}
