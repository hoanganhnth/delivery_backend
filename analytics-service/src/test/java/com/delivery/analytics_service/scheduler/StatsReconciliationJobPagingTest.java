package com.delivery.analytics_service.scheduler;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import com.delivery.analytics_service.entity.DailyOrderStats;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StatsReconciliationJobPagingTest {

    @Test
    void reconcilesAllPagesWithoutLoadingTheWholeDayAtOnce() {
        AnalyticsEventRepository events = mock(AnalyticsEventRepository.class);
        DailyOrderStatsRepository stats = mock(DailyOrderStatsRepository.class);
        StatsReconciliationJob job = new StatsReconciliationJob(events, stats);
        LocalDate date = LocalDate.of(2026, 7, 24);

        AnalyticsEvent created = event("ORDER_CREATED", 7L, null);
        AnalyticsEvent delivered = event("ORDER_DELIVERED", 7L, new BigDecimal("120000"));
        AnalyticsEvent cancelled = event("ORDER_CANCELLED", 11L, null);
        PageRequest secondPageRequest = PageRequest.of(1, 500,
                org.springframework.data.domain.Sort.by("id").ascending());
        @SuppressWarnings("unchecked")
        Page<AnalyticsEvent> firstPage = mock(Page.class);
        @SuppressWarnings("unchecked")
        Page<AnalyticsEvent> secondPage = mock(Page.class);
        when(firstPage.getContent()).thenReturn(List.of(created, delivered));
        when(firstPage.hasNext()).thenReturn(true);
        when(firstPage.nextPageable()).thenReturn(secondPageRequest);
        when(secondPage.getContent()).thenReturn(List.of(cancelled));
        when(secondPage.hasNext()).thenReturn(false);
        when(events.findByEventTimeBetween(any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable requested = invocation.getArgument(2);
                    return requested.getPageNumber() == 0
                            ? firstPage
                            : secondPage;
                });
        when(stats.findByStatDateAndRestaurantIdIsNull(date)).thenReturn(Optional.empty());
        when(stats.findByStatDateAndRestaurantId(eq(date), anyLong())).thenReturn(Optional.empty());

        job.reconcileDate(date);

        ArgumentCaptor<DailyOrderStats> saved = ArgumentCaptor.forClass(DailyOrderStats.class);
        verify(stats, times(3)).save(saved.capture());
        DailyOrderStats platform = saved.getAllValues().stream()
                .filter(value -> value.getRestaurantId() == null)
                .findFirst().orElseThrow();
        assertThat(platform.getTotalOrders()).isEqualTo(1);
        assertThat(platform.getDeliveredOrders()).isEqualTo(1);
        assertThat(platform.getCancelledOrders()).isEqualTo(1);
        assertThat(platform.getTotalRevenue()).isEqualByComparingTo("120000");
        verify(events, times(2)).findByEventTimeBetween(any(), any(), any(Pageable.class));
    }

    private AnalyticsEvent event(String type, Long restaurantId, BigDecimal amount) {
        return AnalyticsEvent.builder()
                .eventType(type)
                .restaurantId(restaurantId)
                .amount(amount)
                .build();
    }
}
