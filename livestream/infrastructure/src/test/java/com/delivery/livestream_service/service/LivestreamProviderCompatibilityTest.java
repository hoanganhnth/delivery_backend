package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.request.CreateLivestreamRequest;
import com.delivery.livestream_service.enums.StreamProvider;
import com.delivery.livestream_service.exception.InvalidLivestreamStatusException;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.LivestreamRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LivestreamProviderCompatibilityTest {
    @Test
    void livekitRemainsDeserializableButIsRejectedAsUnsupportedBadRequest() {
        LivestreamRepository repository = mock(LivestreamRepository.class);
        LivestreamService service = new LivestreamService(repository, mock(LivestreamEventPublisher.class),
                mock(LivestreamMapper.class), mock(StreamTokenService.class));
        CreateLivestreamRequest request = new CreateLivestreamRequest();
        request.setStreamProvider(StreamProvider.LIVEKIT);

        assertThatThrownBy(() -> service.createLivestream(request, 7L, "SELLER"))
                .isInstanceOf(InvalidLivestreamStatusException.class);
        verifyNoInteractions(repository);
    }
}
