package com.delivery.livestream_service.service;

import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LivestreamProductListBoundsTest {

    private final LivestreamProductRepository products = mock(LivestreamProductRepository.class);
    private final LivestreamProductService service = new LivestreamProductService(
            products, mock(LivestreamRepository.class), mock(LivestreamEventPublisher.class),
            mock(LivestreamMapper.class));

    @Test
    void productCompatibilityListsAreCappedAtOneHundredRows() {
        UUID livestreamId = UUID.randomUUID();
        when(products.findByLivestreamId(eq(livestreamId), any())).thenReturn(List.of());
        when(products.findByLivestreamIdAndIsPinned(eq(livestreamId), eq(true), any()))
                .thenReturn(List.of());

        service.getProductsByLivestream(livestreamId);
        service.getPinnedProducts(livestreamId);

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(products).findByLivestreamId(eq(livestreamId), pages.capture());
        verify(products).findByLivestreamIdAndIsPinned(eq(livestreamId), eq(true), pages.capture());
        assertThat(pages.getAllValues()).allSatisfy(page -> {
            assertThat(page.getPageNumber()).isZero();
            assertThat(page.getPageSize()).isEqualTo(100);
        });
    }
}
