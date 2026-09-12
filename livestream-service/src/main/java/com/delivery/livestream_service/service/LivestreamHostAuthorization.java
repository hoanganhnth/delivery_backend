package com.delivery.livestream_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.client.RestaurantOwnershipClient;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import org.springframework.stereotype.Service;

@Service
public class LivestreamHostAuthorization {
    private final RestaurantOwnershipClient restaurants;

    public LivestreamHostAuthorization(RestaurantOwnershipClient restaurants) {
        this.restaurants = restaurants;
    }

    public void requireHost(AuthenticatedActor actor, Long restaurantId) {
        if (actor == null || actor.getUserId() == null || (!actor.isAdmin() && !actor.isShopOwner())) {
            throw new UnauthorizedLivestreamAccessException("ADMIN or SHOP_OWNER role is required");
        }
        if (actor.isAdmin()) {
            return;
        }
        restaurants.requireOwnedBy(restaurantId, actor.getPrincipalId(), actor.getLegacyUserId());
    }
}
