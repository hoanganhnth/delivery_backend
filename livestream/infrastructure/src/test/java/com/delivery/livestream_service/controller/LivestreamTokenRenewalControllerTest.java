package com.delivery.livestream_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.dto.response.TokenResponse;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.TokenRole;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.exception.UnauthorizedLivestreamAccessException;
import com.delivery.livestream_service.service.LivestreamHostAuthorization;
import com.delivery.livestream_service.service.LivestreamService;
import com.delivery.livestream_service.service.StreamTokenService;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LivestreamTokenRenewalControllerTest {

    private final LivestreamService livestreams = mock(LivestreamService.class);
    private final StreamTokenService tokens = mock(StreamTokenService.class);
    private final LivestreamHostAuthorization hosts = mock(LivestreamHostAuthorization.class);
    private final LivestreamTokenRenewalController controller =
            new LivestreamTokenRenewalController(livestreams, tokens, hosts);

    @Test
    void owningHostReceivesServerDerivedHostToken() {
        UUID id = UUID.randomUUID();
        LivestreamResponse room = liveRoom(id, 9L);
        AuthenticatedActor owner = new AuthenticatedActor(90L, 9L, "owner@example.test", Set.of("SHOP_OWNER"));
        TokenResponse token = new TokenResponse("opaque", room.getRoomId(), id, LocalDateTime.now().plusHours(1));
        when(livestreams.getLivestreamById(id)).thenReturn(room);
        when(tokens.generateToken(id, 9L, TokenRole.HOST, 3600)).thenReturn(token);

        var response = controller.renew(id, owner).getBody().getData();

        assertThat(response.getRole()).isEqualTo("HOST");
        assertThat(response.getUid()).isEqualTo(9);
        assertThat(response.getChannelName()).isEqualTo(room.getChannelName());
        assertThat(response.getToken()).isEqualTo("opaque");
        verify(hosts).requireHost(owner, 42L);
    }

    @Test
    void adminMonitoringAnotherHostsRoomReceivesViewerToken() {
        UUID id = UUID.randomUUID();
        LivestreamResponse room = liveRoom(id, 7L);
        AuthenticatedActor admin = new AuthenticatedActor(90L, 9L, "admin@example.test", Set.of("ADMIN"));
        TokenResponse token = new TokenResponse("opaque", room.getRoomId(), id, LocalDateTime.now().plusHours(1));
        when(livestreams.getLivestreamById(id)).thenReturn(room);
        when(tokens.generateToken(id, 9L, TokenRole.VIEWER, 3600)).thenReturn(token);

        var response = controller.renew(id, admin).getBody().getData();

        assertThat(response.getRole()).isEqualTo("VIEWER");
        verifyNoInteractions(hosts);
    }

    @Test
    void rejectsRenewalWhenRoomIsNotLive() {
        UUID id = UUID.randomUUID();
        LivestreamResponse room = liveRoom(id, 9L);
        room.setStatus(LivestreamStatus.ENDED);
        when(livestreams.getLivestreamById(id)).thenReturn(room);
        AuthenticatedActor owner = new AuthenticatedActor(90L, 9L, "owner@example.test", Set.of("SHOP_OWNER"));

        assertThatThrownBy(() -> controller.renew(id, owner))
                .isInstanceOf(InvalidLivestreamStatusException.class);
        verify(tokens, never()).generateToken(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    void rejectsAnonymousRenewalBeforeReadingRoom() {
        assertThatThrownBy(() -> controller.renew(UUID.randomUUID(), null))
                .isInstanceOf(UnauthorizedLivestreamAccessException.class);
        verifyNoInteractions(livestreams, tokens, hosts);
    }

    private LivestreamResponse liveRoom(UUID id, long sellerId) {
        LivestreamResponse room = new LivestreamResponse();
        room.setId(id);
        room.setSellerId(sellerId);
        room.setRestaurantId(42L);
        room.setStatus(LivestreamStatus.LIVE);
        room.setRoomId("room-42");
        room.setChannelName("channel-42");
        return room;
    }
}
