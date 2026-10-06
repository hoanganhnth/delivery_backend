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
    void hostCreateAndStartPreservePayloadAndAuthorizeRestaurantBeforeMutation() {
        var actor = new AuthenticatedActor(110L, 11L, "owner@example.test", Set.of("SHOP_OWNER"));
        var request = new com.delivery.livestream_service.dto.request.CreateLivestreamRequest();
        request.setTitle("Friday kitchen");
        request.setRestaurantId(42L);
        request.setStreamProvider(com.delivery.livestream_service.enums.StreamProvider.AGORA);
        var room = new LivestreamResponse();
        var id = UUID.randomUUID();
        room.setId(id);
        room.setRestaurantId(42L);
        when(livestreams.createLivestream(request, 11L, "SHOP_OWNER")).thenReturn(room);
        when(livestreams.getLivestreamById(id)).thenReturn(room);
        var started = new com.delivery.livestream_service.dto.response.StartLivestreamResponse();
        started.setToken("opaque-fixture-token");
        when(livestreams.startLivestream(id, 11L, "SHOP_OWNER")).thenReturn(started);

        var createdResponse = controller.createLivestream(request, actor);
        org.assertj.core.api.Assertions.assertThat(createdResponse.getStatusCode().value()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(createdResponse.getBody().getData()).isSameAs(room);
        org.assertj.core.api.Assertions.assertThat(createdResponse.getBody().getMessage()).isEqualTo("Tạo livestream thành công");
        var startedResponse = controller.startLivestream(id, actor);
        org.assertj.core.api.Assertions.assertThat(startedResponse.getBody().getData().getToken()).isEqualTo("opaque-fixture-token");
        var order = org.mockito.Mockito.inOrder(hostAuthorization, livestreams);
        order.verify(hostAuthorization).requireHost(actor, 42L);
        order.verify(livestreams).createLivestream(request, 11L, "SHOP_OWNER");
        order.verify(livestreams).getLivestreamById(id);
        order.verify(hostAuthorization).requireHost(actor, 42L);
        order.verify(livestreams).startLivestream(id, 11L, "SHOP_OWNER");
    }

    @Test
    void authenticatedReadsReturnRoomDataAndRestaurantReadsRequireHostAuthority() {
        var actor = new AuthenticatedActor(110L, 11L, "owner@example.test", Set.of("SHOP_OWNER"));
        var id = UUID.randomUUID();
        var room = new LivestreamResponse();
        room.setId(id);
        var rooms = java.util.List.of(room);
        when(livestreams.getActiveLivestreams()).thenReturn(rooms);
        when(livestreams.getLivestreamById(id)).thenReturn(room);
        when(livestreams.getLivestreamsBySeller(11L)).thenReturn(rooms);
        when(livestreams.getLivestreamsByRestaurant(42L)).thenReturn(rooms);

        org.assertj.core.api.Assertions.assertThat(controller.getActiveLivestreams(actor).getBody().getData()).isEqualTo(rooms);
        org.assertj.core.api.Assertions.assertThat(controller.getLivestreamById(id, actor).getBody().getData()).isSameAs(room);
        org.assertj.core.api.Assertions.assertThat(controller.getLivestreamsBySeller(11L, actor).getBody().getData()).isEqualTo(rooms);
        var response = controller.getLivestreamsByRestaurant(42L, actor);
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(response.getBody().getStatus()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(response.getBody().getData()).isEqualTo(rooms);
        var order = org.mockito.Mockito.inOrder(hostAuthorization, livestreams);
        order.verify(hostAuthorization).requireHost(actor, 42L);
        order.verify(livestreams).getLivestreamsByRestaurant(42L);
    }

    @Test
    void absentActorCannotReadRoomsOrStartThem() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> controller.getActiveLivestreams(null))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class).hasMessage("Yêu cầu đăng nhập");
        assertThatThrownBy(() -> controller.startLivestream(id, null))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        verifyNoInteractions(livestreams, hostAuthorization);
    }

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
