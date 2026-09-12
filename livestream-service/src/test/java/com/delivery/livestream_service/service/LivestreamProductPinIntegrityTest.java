package com.delivery.livestream_service.service;

import com.delivery.livestream_service.dto.request.PinProductRequest;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.exception.ProductAlreadyPinnedException;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LivestreamProductPinIntegrityTest {
    @Test
    void pinningAnAlreadyPinnedProductReturnsConflictException() {
        LivestreamProductRepository products = mock(LivestreamProductRepository.class);
        LivestreamRepository livestreams = mock(LivestreamRepository.class);
        LivestreamProductService service = new LivestreamProductService(
                products, livestreams, mock(LivestreamEventPublisher.class), mock(LivestreamMapper.class));
        UUID id = UUID.randomUUID();
        Livestream livestream = new Livestream();
        livestream.setId(id);
        livestream.setSellerId(7L);
        livestream.setStatus(LivestreamStatus.LIVE);
        LivestreamProduct existing = new LivestreamProduct();
        existing.setIsPinned(true);
        when(livestreams.findById(id)).thenReturn(Optional.of(livestream));
        when(products.findByLivestreamIdAndProductId(id, 11L)).thenReturn(Optional.of(existing));
        PinProductRequest request = new PinProductRequest();
        request.setProductId(11L);

        assertThatThrownBy(() -> service.pinProduct(id, request, 7L))
                .isInstanceOf(ProductAlreadyPinnedException.class);
        verify(products, never()).save(any());
    }
}
