package com.delivery.livestream_service.service;

import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.LivestreamRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LivestreamListBoundsTest {

    private final LivestreamRepository repository = mock(LivestreamRepository.class);
    private final LivestreamService service = new LivestreamService(
            repository, mock(LivestreamEventPublisher.class), mock(LivestreamMapper.class),
            mock(StreamTokenService.class));

    @Test
    void compatibilityListsAreCappedAtOneHundredRows() {
        when(repository.findByStatusOrderByCreatedAtDesc(eq(LivestreamStatus.LIVE), any()))
                .thenReturn(List.of());
        when(repository.findBySellerIdOrderByCreatedAtDesc(eq(7L), any()))
                .thenReturn(List.of());
        when(repository.findByRestaurantIdOrderByCreatedAtDesc(eq(11L), any()))
                .thenReturn(List.of());

        service.getActiveLivestreams();
        service.getLivestreamsBySeller(7L);
        service.getLivestreamsByRestaurant(11L);

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByStatusOrderByCreatedAtDesc(eq(LivestreamStatus.LIVE), pages.capture());
        verify(repository).findBySellerIdOrderByCreatedAtDesc(eq(7L), pages.capture());
        verify(repository).findByRestaurantIdOrderByCreatedAtDesc(eq(11L), pages.capture());
        assertThat(pages.getAllValues()).allSatisfy(page -> {
            assertThat(page.getPageNumber()).isZero();
            assertThat(page.getPageSize()).isEqualTo(100);
        });
    }
}
