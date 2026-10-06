package com.delivery.livestream_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.livestream_service.dto.response.TokenResponse;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.TokenRole;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.LivestreamRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LivestreamJoinViewCountTest {

    private final LivestreamRepository rooms = mock(LivestreamRepository.class);
    private final StreamTokenService tokens = mock(StreamTokenService.class);
    private final LivestreamService service = new LivestreamService(
            rooms, mock(LivestreamEventPublisher.class), mock(LivestreamMapper.class), tokens);

    @Test
    void operationalAdminJoinDoesNotMutateCumulativeCustomerViews() {
        Livestream room = liveRoom(12L);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(tokens.generateToken(room.getId(), 9L, TokenRole.VIEWER, 3600))
                .thenReturn(token(room));

        var response = service.joinLivestream(room.getId(), 9L, false);

        assertThat(room.getViewCount()).isEqualTo(12L);
        assertThat(response.getCurrentViewers()).isNull();
        verify(rooms, never()).save(room);
    }

    @Test
    void customerJoinIncrementsAndPersistsCumulativeViews() {
        Livestream room = liveRoom(12L);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(rooms.save(room)).thenReturn(room);
        when(tokens.generateToken(room.getId(), 20L, TokenRole.VIEWER, 3600))
                .thenReturn(token(room));

        service.joinLivestream(room.getId(), 20L, true);

        assertThat(room.getViewCount()).isEqualTo(13L);
        verify(rooms).save(room);
    }

    private Livestream liveRoom(long viewCount) {
        Livestream room = new Livestream();
        room.setId(UUID.randomUUID());
        room.setSellerId(7L);
        room.setRestaurantId(42L);
        room.setTitle("Live");
        room.setRoomId("room-42");
        room.setChannelName("channel-42");
        room.setStatus(LivestreamStatus.LIVE);
        room.setStartedAt(LocalDateTime.now().minusMinutes(2));
        room.setViewCount(viewCount);
        return room;
    }

    private TokenResponse token(Livestream room) {
        return new TokenResponse("opaque", room.getRoomId(), room.getId(), LocalDateTime.now().plusHours(1));
    }
}
