package com.delivery.livestream_service.service;
import com.delivery.livestream_service.client.LivestreamProductAuthorityClient;
import com.delivery.livestream_service.dto.request.PinProductRequest;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.mapper.LivestreamMapper;
import com.delivery.livestream_service.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class LivestreamProductAuthorityIntegrationTest {
    @Test
    void pinUsesCanonicalMetadataAndDoesNotPersistOnAuthorityFailure() {
        var products = mock(LivestreamProductRepository.class);
        var streams = mock(LivestreamRepository.class);
        var authority = mock(LivestreamProductAuthorityClient.class);
        var events = mock(LivestreamEventPublisher.class);
        var service = new LivestreamProductService(products, streams, events, new LivestreamMapper(), authority);
        var room = new Livestream();
        room.setId(UUID.randomUUID()); room.setSellerId(7L); room.setRestaurantId(42L); room.setStatus(LivestreamStatus.LIVE);
        when(streams.findById(room.getId())).thenReturn(Optional.of(room));
        when(products.findByLivestreamIdAndProductId(room.getId(), 10L)).thenReturn(Optional.empty());
        var request = new PinProductRequest();
        request.setProductId(10L); request.setPriceAtLive(new BigDecimal("35000"));
        request.setProductName("Forged"); request.setRestaurantName("Forged");
        when(authority.requireAvailable(42L, 10L)).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> service.pinProduct(room.getId(), request, 7L)).isInstanceOf(RuntimeException.class);
        verify(products, never()).save(any()); verifyNoInteractions(events);
        doReturn(new LivestreamProductAuthorityClient.Product(10L, 42L, "Canonical", null, "Kitchen"))
            .when(authority).requireAvailable(42L, 10L);
        when(products.save(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.pinProduct(room.getId(), request, 7L);
        assertThat(result.getProductName()).isEqualTo("Canonical");
        assertThat(result.getRestaurantName()).isEqualTo("Kitchen");
        assertThat(result.getPriceAtLive()).isEqualByComparingTo("35000");
        assertThat(result.getIsPinned()).isTrue();
    }
}
