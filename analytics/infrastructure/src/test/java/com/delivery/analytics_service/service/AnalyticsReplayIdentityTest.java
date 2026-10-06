package com.delivery.analytics_service.service;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AnalyticsReplayIdentityTest {
    private static final String PAYLOAD = "{\"eventId\":\"replay\"}";
    private final AnalyticsEventRepository events = mock(AnalyticsEventRepository.class);
    private final DailyOrderStatsRepository orders = mock(DailyOrderStatsRepository.class);
    private final DailyRevenueStatsRepository revenue = mock(DailyRevenueStatsRepository.class);
    private final EventProcessingService service = new EventProcessingService(events, orders, revenue);

    @ParameterizedTest(name = "contradictory {0}")
    @MethodSource("contradictions")
    void rejectsEachChangedIdentityFieldBeforeProjection(String field, Consumer<AnalyticsEvent> change) {
        AnalyticsEvent stored = receipt();
        change.accept(stored);
        when(events.findByDeduplicationKey("ORDER_CREATED:event:replay")).thenReturn(Optional.of(stored));

        assertThatThrownBy(this::process).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contradictory");
        verifyNoInteractions(orders, revenue);
    }

    @Test
    void amountScaleDoesNotTurnAnExactLegacyReplayIntoAConflict() {
        when(events.findByDeduplicationKey("ORDER_CREATED:event:replay"))
                .thenReturn(Optional.of(receipt()));
        process();
        verifyNoInteractions(orders, revenue);
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void cancelledReplayWithNoAmountIsIdempotent() {
        AnalyticsEvent stored = AnalyticsEvent.builder().eventType("ORDER_CANCELLED")
                .orderId(9L).restaurantId(3L).orderStatus("CANCELLED").rawPayload(PAYLOAD).build();
        when(events.findByDeduplicationKey("ORDER_CANCELLED:event:replay")).thenReturn(Optional.of(stored));

        service.processOrderCancelled(9L, 3L, PAYLOAD);

        verifyNoInteractions(orders, revenue);
    }

    @Test
    void deliveredReplayCannotDropTheAcceptedAmount() {
        AnalyticsEvent stored = AnalyticsEvent.builder().eventType("ORDER_DELIVERED")
                .orderId(9L).restaurantId(3L).restaurantName("Restaurant")
                .amount(BigDecimal.TEN).orderStatus("DELIVERED").rawPayload(PAYLOAD).build();
        when(events.findByDeduplicationKey("ORDER_DELIVERED:event:replay")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.processOrderDelivered(9L, 3L, "Restaurant", null, PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contradictory");
        verifyNoInteractions(orders, revenue);
    }

    private static Stream<Arguments> contradictions() {
        return Stream.of(
                changed("type", e -> e.setEventType("ORDER_DELIVERED")),
                changed("order", e -> e.setOrderId(10L)),
                changed("user", e -> e.setUserId(4L)),
                changed("restaurant", e -> e.setRestaurantId(4L)),
                changed("restaurant name", e -> e.setRestaurantName("Other")),
                changed("amount", e -> e.setAmount(BigDecimal.ONE)),
                changed("null amount", e -> e.setAmount(null)),
                changed("status", e -> e.setOrderStatus("DELIVERED")),
                changed("payment method", e -> e.setPaymentMethod("WALLET")),
                changed("version", e -> e.setAggregateVersion(1L)),
                changed("raw payload", e -> e.setRawPayload("{}")),
                changed("fingerprint", e -> e.setPayloadFingerprint("different")));
    }

    private static Arguments changed(String name, Consumer<AnalyticsEvent> change) {
        return Arguments.of(name, change);
    }

    private AnalyticsEvent receipt() {
        return AnalyticsEvent.builder().eventType("ORDER_CREATED").orderId(9L).userId(2L)
                .restaurantId(3L).restaurantName("Restaurant").amount(new BigDecimal("10.00"))
                .orderStatus("PENDING").paymentMethod("COD").rawPayload(PAYLOAD).build();
    }

    private void process() {
        service.processOrderCreated(9L, 2L, 3L, "Restaurant", BigDecimal.TEN, "COD", PAYLOAD);
    }
}
