package com.delivery.analytics_service.service;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsPostgresReceiptClaimTest {
    private static final String KEY = "ORDER_CREATED:event:race";
    private static final String PAYLOAD = "{\"eventId\":\"race\"}";
    @Mock AnalyticsEventRepository events;
    @Mock DailyOrderStatsRepository orders;
    @Mock DailyRevenueStatsRepository revenue;
    EventProcessingService service;

    @BeforeEach
    void setup() {
        service = new EventProcessingService(events, orders, revenue);
        ReflectionTestUtils.setField(service, "dataSourceUrl", "jdbc:postgresql://localhost/analytics");
    }

    @Test
    void insertedReceiptAppliesProjectionThroughThePostgresPath() {
        insertReturns(1);
        process();
        verify(orders).incrementCreatedPostgres(any(), isNull());
        verify(orders).incrementCreatedPostgres(any(), eq(3L));
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void concurrentExactWinnerIsLoadedAndDoesNotApplyProjectionAgain() {
        when(events.findByDeduplicationKey(KEY)).thenReturn(Optional.empty(), Optional.of(receipt()));
        insertReturns(0);
        process();
        verifyNoInteractions(orders, revenue);
    }

    @Test
    void concurrentContradictoryWinnerFailsBeforeProjection() {
        AnalyticsEvent winner = receipt();
        winner.setOrderId(10L);
        when(events.findByDeduplicationKey(KEY)).thenReturn(Optional.empty(), Optional.of(winner));
        insertReturns(0);
        assertThatThrownBy(this::process).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contradictory");
        verifyNoInteractions(orders, revenue);
    }

    @Test
    void conflictWithoutAVisibleCommittedReceiptFailsInsteadOfAcknowledging() {
        insertReturns(0);
        assertThatThrownBy(this::process).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without a committed row");
        verifyNoInteractions(orders, revenue);
    }

    private void insertReturns(int count) {
        when(events.insertIfAbsentPostgres(anyString(), anyString(), any(), any(), any(), any(),
                any(), any(), any(), anyString(), anyString(), any())).thenReturn(count);
    }

    private void process() {
        service.processOrderCreated(9L, 2L, 3L, "Restaurant", BigDecimal.TEN, "COD", PAYLOAD);
    }

    private AnalyticsEvent receipt() {
        return AnalyticsEvent.builder().deduplicationKey(KEY).eventType("ORDER_CREATED")
                .orderId(9L).userId(2L).restaurantId(3L).restaurantName("Restaurant")
                .amount(new BigDecimal("10.00")).orderStatus("PENDING").paymentMethod("COD")
                .rawPayload(PAYLOAD).build();
    }
}
