package com.delivery.livestream_service.service;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.client.RestaurantOwnershipClient;
import com.delivery.livestream.application.HostAuthorizationUseCase;
import org.springframework.stereotype.Service;

@Service
public class LivestreamHostAuthorization {
    private final HostAuthorizationUseCase useCase;

    public LivestreamHostAuthorization(RestaurantOwnershipClient restaurants) {
        this.useCase = new HostAuthorizationUseCase(restaurants::requireOwnedBy);
    }

    public void requireHost(AuthenticatedActor actor, Long restaurantId) {
        LivestreamCompatibility.run(() -> useCase.requireHost(
                actor == null ? null : actor.getUserId(),
                actor != null && actor.isAdmin(), actor != null && actor.isShopOwner(),
                restaurantId, actor == null ? null : actor.getPrincipalId(),
                actor == null ? null : actor.getLegacyUserId()));
    }
}
