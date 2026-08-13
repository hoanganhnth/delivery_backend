package com.delivery.analytics_service.service;

import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.util.Optional;

class EventProcessingServiceDeduplicationTest {

    @Test
    void stableProducerEventIdWinsOverLegacyOrderFallback() {
        assertEquals("ORDER_CREATED:event:evt-123",
                EventProcessingService.resolveDeduplicationKey(
                        "ORDER_CREATED", 9L, "{\"eventId\":\"evt-123\"}"));
        assertEquals("ORDER_CREATED:order:9",
                EventProcessingService.resolveDeduplicationKey("ORDER_CREATED", 9L, "{}"));
    }

    @Test
    void duplicateEventDoesNotMutateAggregates() {
        AnalyticsEventRepository events = mock(AnalyticsEventRepository.class);
        DailyOrderStatsRepository orders = mock(DailyOrderStatsRepository.class);
        DailyRevenueStatsRepository revenue = mock(DailyRevenueStatsRepository.class);
        EventProcessingService service = new EventProcessingService(events, orders, revenue);
        when(events.findByDeduplicationKey("ORDER_CREATED:event:evt-123"))
                .thenReturn(Optional.of(com.delivery.analytics_service.entity.AnalyticsEvent.builder()
                        .deduplicationKey("ORDER_CREATED:event:evt-123")
                        .eventType("ORDER_CREATED").orderId(9L).userId(2L)
                        .restaurantId(3L).restaurantName("Restaurant")
                        .amount(BigDecimal.TEN).orderStatus("PENDING")
                        .paymentMethod("COD").rawPayload("{\"eventId\":\"evt-123\"}")
                        .build()));

        service.processOrderCreated(9L, 2L, 3L, "Restaurant", BigDecimal.TEN,
                "COD", "{\"eventId\":\"evt-123\"}");

        verifyNoInteractions(orders, revenue);
    }

    @Test
    void contradictoryReuseFailsClosedBeforeAggregateMutation() {
        AnalyticsEventRepository events = mock(AnalyticsEventRepository.class);
        DailyOrderStatsRepository orders = mock(DailyOrderStatsRepository.class);
        DailyRevenueStatsRepository revenue = mock(DailyRevenueStatsRepository.class);
        EventProcessingService service = new EventProcessingService(events, orders, revenue);
        when(events.findByDeduplicationKey("ORDER_CREATED:event:evt-123"))
                .thenReturn(Optional.of(com.delivery.analytics_service.entity.AnalyticsEvent.builder()
                        .deduplicationKey("ORDER_CREATED:event:evt-123")
                        .eventType("ORDER_CREATED").orderId(9L).userId(2L)
                        .restaurantId(3L).restaurantName("Restaurant")
                        .amount(BigDecimal.TEN).orderStatus("PENDING")
                        .paymentMethod("COD").rawPayload("{\"eventId\":\"evt-123\"}")
                        .build()));

        assertThrows(IllegalArgumentException.class, () -> service.processOrderCreated(
                10L, 2L, 3L, "Restaurant", BigDecimal.TEN, "COD",
                "{\"eventId\":\"evt-123\",\"orderId\":10}"));
        verifyNoInteractions(orders, revenue);
    }
}
