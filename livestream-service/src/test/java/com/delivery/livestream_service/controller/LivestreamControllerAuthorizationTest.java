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

        verify(livestreams).joinLivestream(livestreamId, 10L, true);
        verifyNoInteractions(hostAuthorization);
    }

    @Test
    void adminMonitoringDoesNotIncreaseCustomerViewCount() {
        UUID livestreamId = UUID.randomUUID();
        AuthenticatedActor admin = new AuthenticatedActor(90L, 9L, "admin@example.test", Set.of("ADMIN"));

        controller.joinLivestream(livestreamId, admin);

        verify(livestreams).joinLivestream(livestreamId, 9L, false);
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

    @Test
    void productReadsUseTheCanonicalLivestreamAuthorizationFailure() {
        var products = mock(com.delivery.livestream_service.service.LivestreamProductService.class);
        var productController = new LivestreamProductController(products, livestreams, hostAuthorization);

        assertThatThrownBy(() -> productController.getPinnedProducts(UUID.randomUUID(), null))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        verifyNoInteractions(products);
    }

    @Test
    void crossOwnerAdminMustUseAuditedModerationForEnd() {
        UUID id = UUID.randomUUID();
        LivestreamResponse room = new LivestreamResponse();
        room.setRestaurantId(42L); room.setSellerId(11L);
        when(livestreams.getLivestreamById(id)).thenReturn(room);
        AuthenticatedActor admin = new AuthenticatedActor(90L, 9L, "admin@example.test", Set.of("ADMIN"));
        assertThatThrownBy(() -> controller.endLivestream(id, admin))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        org.mockito.Mockito.verify(livestreams, org.mockito.Mockito.never()).endLivestream(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void owningAdminRetainsHostEndAndUnpinControls() {
        UUID id = UUID.randomUUID();
        LivestreamResponse room = new LivestreamResponse();
        room.setRestaurantId(42L); room.setSellerId(9L);
        when(livestreams.getLivestreamById(id)).thenReturn(room);
        var admin = new AuthenticatedActor(90L, 9L, "admin@example.test", Set.of("ADMIN"));
        var products = mock(com.delivery.livestream_service.service.LivestreamProductService.class);

        controller.endLivestream(id, admin);
        new LivestreamProductController(products, livestreams, hostAuthorization).unpinProduct(id, 23L, admin);

        verify(livestreams).endLivestream(id, 9L, "ADMIN");
        verify(products).unpinProduct(id, 23L, 9L);
    }

    @Test
    void crossOwnerAdminCannotBypassUnpinAuditThroughProductRoutes() {
        UUID id = UUID.randomUUID();
        LivestreamResponse room = new LivestreamResponse();
        room.setRestaurantId(42L); room.setSellerId(11L);
        when(livestreams.getLivestreamById(id)).thenReturn(room);
        var products = mock(com.delivery.livestream_service.service.LivestreamProductService.class);
        var controller = new LivestreamProductController(products, livestreams, hostAuthorization);
        var admin = new AuthenticatedActor(90L, 9L, "admin@example.test", Set.of("ADMIN"));
        assertThatThrownBy(() -> controller.unpinProduct(id, 23L, admin))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        assertThatThrownBy(() -> controller.removeProduct(id, 23L, admin))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        verifyNoInteractions(products);
    }

    @Test
    void crossOwnerAdminCannotPinProductsIntoAnotherHostsRoom() {
        UUID id = UUID.randomUUID();
        LivestreamResponse room = new LivestreamResponse();
        room.setRestaurantId(42L); room.setSellerId(11L);
        when(livestreams.getLivestreamById(id)).thenReturn(room);
        var products = mock(com.delivery.livestream_service.service.LivestreamProductService.class);
        var productController = new LivestreamProductController(products, livestreams, hostAuthorization);
        var admin = new AuthenticatedActor(90L, 9L, "admin@example.test", Set.of("ADMIN"));
        var request = new com.delivery.livestream_service.dto.request.PinProductRequest();

        assertThatThrownBy(() -> productController.pinProduct(id, request, admin))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        verifyNoInteractions(products, hostAuthorization);
    }
}
