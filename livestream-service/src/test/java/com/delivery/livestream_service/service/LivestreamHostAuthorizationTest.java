package com.delivery.livestream_service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.client.RestaurantOwnershipClient;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LivestreamHostAuthorizationTest {

    private final RestaurantOwnershipClient restaurants = mock(RestaurantOwnershipClient.class);
    private final LivestreamHostAuthorization authorization = new LivestreamHostAuthorization(restaurants);

    @Test
    void nonShopOwnerIsDeniedWithoutAnOwnershipLookup() {
        AuthenticatedActor customer = new AuthenticatedActor(11L, 11L, "customer@example.test", Set.of("USER"));

        assertThatThrownBy(() -> authorization.requireHost(customer, 42L))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);

        verifyNoInteractions(restaurants);
    }
}
