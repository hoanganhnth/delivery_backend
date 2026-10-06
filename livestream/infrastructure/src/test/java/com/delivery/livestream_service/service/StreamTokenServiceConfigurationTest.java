package com.delivery.livestream_service.service;

import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.TokenRole;
import com.delivery.livestream_service.repository.LivestreamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StreamTokenServiceConfigurationTest {

    @Test
    void invalidAgoraCredentialsCannotProduceAnEmptySuccessfulToken() {
        UUID id = UUID.randomUUID();
        Livestream room = new Livestream();
        room.setId(id);
        room.setStatus(LivestreamStatus.LIVE);
        room.setRoomId("room-live");
        room.setChannelName("channel-live");
        LivestreamRepository repository = mock(LivestreamRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(room));
        StreamTokenService service = new StreamTokenService(repository);
        ReflectionTestUtils.setField(service, "agoraAppId", "invalid");
        ReflectionTestUtils.setField(service, "agoraAppCertificate", "invalid");

        assertThatThrownBy(() -> service.generateToken(id, 9L, TokenRole.VIEWER, 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Không thể tạo Agora token");
    }
}
