package com.delivery.livestream_service.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.service.LivestreamHostAuthorization;
import com.delivery.livestream_service.service.LivestreamService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LivestreamControllerAuthorizationTest {

    private final LivestreamService livestreams = mock(LivestreamService.class);
    private final LivestreamHostAuthorization hostAuthorization = mock(LivestreamHostAuthorization.class);
    private final LivestreamController controller = new LivestreamController(livestreams, hostAuthorization);

    @Test
    void viewerJoinDoesNotRequireRestaurantHostOwnership() {
        UUID livestreamId = UUID.randomUUID();
        AuthenticatedActor viewer = new AuthenticatedActor(10L, 10L, "viewer@example.test", Set.of("USER"));

        controller.joinLivestream(livestreamId, viewer);

        verify(livestreams).joinLivestream(livestreamId, 10L);
        verifyNoInteractions(hostAuthorization);
    }

    @Test
    void endRequiresHostOwnershipForTheStreamRestaurant() {
        UUID livestreamId = UUID.randomUUID();
        AuthenticatedActor owner = new AuthenticatedActor(11L, 11L, "owner@example.test", Set.of("SHOP_OWNER"));
        LivestreamResponse stream = new LivestreamResponse();
        stream.setRestaurantId(42L);
        when(livestreams.getLivestreamById(livestreamId)).thenReturn(stream);

        controller.endLivestream(livestreamId, owner);

        verify(hostAuthorization).requireHost(owner, 42L);
    }

    @Test
    void callerControlledTokenEndpointFailsClosedWithTheAuthorizationException() {
        StreamTokenController tokens = new StreamTokenController(mock());

        assertThatThrownBy(() -> tokens.generateToken(UUID.randomUUID(), null, null))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
    }
}
