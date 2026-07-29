package com.delivery.analytics_service.service;

import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        when(events.existsByDeduplicationKey("ORDER_CREATED:event:evt-123")).thenReturn(true);

        service.processOrderCreated(9L, 2L, 3L, "Restaurant", BigDecimal.TEN,
                "COD", "{\"eventId\":\"evt-123\"}");

        verifyNoInteractions(orders, revenue);
    }
}
