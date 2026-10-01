package com.delivery.livestream_service.checkout;

import com.delivery.livestream_service.dto.request.LivestreamCheckoutQuoteRequest;
import com.delivery.livestream_service.repository.LivestreamProductRepository;
import com.delivery.livestream_service.repository.LivestreamRepository;
import com.delivery.livestream_service.repository.LivestreamCheckoutReceiptRepository;
import com.delivery.livestream_service.service.LivestreamCheckoutQuoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LivestreamCheckoutValidationTest {
    static Stream<LivestreamCheckoutQuoteRequest> invalidScopes() {
        return Stream.of(null,
                scope(null, 42L, List.of(10L)),
                scope(UUID.randomUUID(), null, List.of(10L)),
                scope(UUID.randomUUID(), 0L, List.of(10L)),
                scope(UUID.randomUUID(), 42L, null),
                scope(UUID.randomUUID(), 42L, List.of()),
                scope(UUID.randomUUID(), 42L, Arrays.asList(10L, null)),
                scope(UUID.randomUUID(), 42L, List.of(0L)),
                scope(UUID.randomUUID(), 42L, List.of(10L, 10L)),
                scope(UUID.randomUUID(), 42L, IntStream.rangeClosed(1, 51).mapToObj(Long::valueOf).toList()));
    }

    @ParameterizedTest
    @MethodSource("invalidScopes")
    void rejectsInvalidScopeBeforeRepositoryAccess(LivestreamCheckoutQuoteRequest request) {
        var rooms = mock(LivestreamRepository.class);
        var products = mock(LivestreamProductRepository.class);
        var receipts = mock(LivestreamCheckoutReceiptRepository.class);
        var service = new LivestreamCheckoutQuoteService(rooms, products, receipts, new ObjectMapper());

        assertThatThrownBy(() -> service.orderContext(request, 123L, "correlation", "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid livestream checkout quote scope");
        verifyNoInteractions(rooms, products, receipts);
    }

    private static LivestreamCheckoutQuoteRequest scope(UUID stream, Long restaurant, List<Long> products) {
        var request = new LivestreamCheckoutQuoteRequest();
        request.setLivestreamId(stream);
        request.setRestaurantId(restaurant);
        request.setProductIds(products);
        return request;
    }
}
