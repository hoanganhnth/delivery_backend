package com.delivery.livestream_service.controller;

import com.delivery.livestream_service.dto.request.LivestreamCheckoutQuoteRequest;
import com.delivery.livestream_service.dto.response.LivestreamCheckoutQuoteResponse;
import com.delivery.livestream_service.service.LivestreamCheckoutQuoteService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalLivestreamCheckoutControllerTest {
    private final LivestreamCheckoutQuoteService service = mock(LivestreamCheckoutQuoteService.class);
    private final InternalLivestreamCheckoutController controller =
            new InternalLivestreamCheckoutController(service, "test-only-secret");

    @Test
    void deniesMissingOrWrongInternalTokenBeforeReadingRoom() {
        var request = request();

        assertThat(controller.quote(request, null).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.quote(request, "wrong").getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(service);
    }

    @Test
    void returnsAuthoritativeQuoteForMatchingInternalToken() {
        var request = request();
        var response = new LivestreamCheckoutQuoteResponse(
                request.getLivestreamId(), 42L, List.of());
        when(service.quote(request)).thenReturn(response);

        var result = controller.quote(request, "test-only-secret");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().getData()).isEqualTo(response);
    }

    private LivestreamCheckoutQuoteRequest request() {
        var request = new LivestreamCheckoutQuoteRequest();
        request.setLivestreamId(UUID.randomUUID());
        request.setRestaurantId(42L);
        request.setProductIds(List.of(10L));
        return request;
    }
}
