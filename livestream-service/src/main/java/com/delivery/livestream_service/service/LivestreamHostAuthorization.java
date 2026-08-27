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
        if (actor == null || actor.getPrincipalId() == null || actor.getLegacyUserId() == null
                || !actor.isShopOwner()) {
            throw new UnauthorizedLivestreamAccessException("SHOP_OWNER role is required");
        }
        restaurants.requireOwnedBy(restaurantId, actor.getPrincipalId(), actor.getLegacyUserId());
    }
}
