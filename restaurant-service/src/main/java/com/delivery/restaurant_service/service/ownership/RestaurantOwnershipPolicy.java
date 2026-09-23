package com.delivery.restaurant_service.service.ownership;

import com.delivery.restaurant.application.api.RestaurantManagementAccessUseCase;
import com.delivery.restaurant.domain.ownership.ManagementAccessException;
import com.delivery.restaurant.domain.ownership.RestaurantActorRole;
import com.delivery.restaurant.domain.ownership.RestaurantManagementFacts;
import com.delivery.restaurant_service.common.constants.RoleConstants;
import com.delivery.restaurant_service.entity.Restaurant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final RestaurantManagementAccessUseCase managementAccessUseCase;

    @Autowired
    public RestaurantOwnershipPolicy(
            @Value("${app.identity.principal-ownership.enforced:false}") boolean principalOwnershipEnforced,
            RestaurantManagementAccessUseCase managementAccessUseCase) {
        this.principalOwnershipEnforced = principalOwnershipEnforced;
        this.managementAccessUseCase = managementAccessUseCase;
    }

    /** Test-only transitional constructor; production must use the application decision. */
    public RestaurantOwnershipPolicy(boolean principalOwnershipEnforced) {
        this(principalOwnershipEnforced,
                new com.delivery.restaurant.application.DefaultRestaurantManagementAccessUseCase());
    }

    public ManagementAccess assertCanManage(Restaurant restaurant, Long principalId,
                                             Long legacyUserId, String role) {
        try {
            var result = managementAccessUseCase.resolve(
                    new RestaurantManagementFacts(
                            restaurant.getOwnerPrincipalId(), restaurant.getCreatorId()),
                    principalId, legacyUserId, actorRole(role), principalOwnershipEnforced);
            if (result.usedLegacyFallback()) {
                log.info("Restaurant management used legacy ownership fallback");
                return ManagementAccess.legacyFallback();
            }
            return ManagementAccess.direct();
        } catch (ManagementAccessException | IllegalArgumentException ex) {
            throw denied();
        }
    }

    public boolean isPrincipalOwnershipEnforced() {
        return principalOwnershipEnforced;
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Actor does not own this restaurant");
    }

    private RestaurantActorRole actorRole(String role) {
        if (RoleConstants.ADMIN.equalsIgnoreCase(role)) return RestaurantActorRole.ADMIN;
        if (RoleConstants.OWNER.equalsIgnoreCase(role)) return RestaurantActorRole.SHOP_OWNER;
        return RestaurantActorRole.OTHER;
    }
}
