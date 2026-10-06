package com.delivery.analytics_service.service;

import com.delivery.analytics_service.entity.DailyOrderStats;
import com.delivery.analytics_service.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsCancelledProjectionTest {
    @ParameterizedTest
    @CsvSource({"false,false,0", "false,false,2", "false,true,0", "false,true,2",
            "true,false,0", "true,true,0"})
    void cancellationOnlyIncrementsCancelledAndDecrementsPositivePending(
            boolean postgres, boolean restaurant, long pending) {
        var events = mock(AnalyticsEventRepository.class);
        var orders = mock(DailyOrderStatsRepository.class);
        var revenue = mock(DailyRevenueStatsRepository.class);
        var items = mock(DailyItemSalesRepository.class);
        var service = new EventProcessingService(events, orders, revenue, items);
        var platform = row(null, pending);
        var shop = row(7L, pending);
        if (postgres) {
            ReflectionTestUtils.setField(service, "dataSourceUrl", "jdbc:postgresql://localhost/analytics");
            when(events.insertIfAbsentPostgres(anyString(), anyString(), any(), any(), any(), any(),
                    any(), any(), any(), anyString(), anyString(), any())).thenReturn(1);
        } else {
            when(orders.findByStatDateAndRestaurantIdIsNull(any())).thenReturn(Optional.of(platform));
            if (restaurant) when(orders.findByStatDateAndRestaurantId(any(), eq(7L)))
                    .thenReturn(Optional.of(shop));
        }

        service.processOrderCancelled(1L, restaurant ? 7L : null, "{\"eventId\":\"cancelled\"}");

        if (postgres) {
            verify(orders).incrementCancelledPostgres(any(LocalDate.class), isNull());
            if (restaurant) verify(orders).incrementCancelledPostgres(any(LocalDate.class), eq(7L));
            verifyNoMoreInteractions(orders);
        } else {
            verify(orders).save(platform);
            assertProjection(platform, pending);
            if (restaurant) {
                verify(orders).save(shop);
                assertProjection(shop, pending);
            }
        }
        verifyNoInteractions(revenue, items);
    }

    private static void assertProjection(DailyOrderStats row, long pending) {
        assertThat(row.getPendingOrders()).isEqualTo(Math.max(0, pending - 1));
        assertThat(row.getCancelledOrders()).isEqualTo(2);
        assertThat(row.getTotalOrders()).isEqualTo(4);
        assertThat(row.getDeliveredOrders()).isEqualTo(1);
        assertThat(row.getTotalRevenue()).isEqualByComparingTo("10");
        assertThat(row.getAvgOrderValue()).isEqualByComparingTo("10");
    }

    private static DailyOrderStats row(Long restaurantId, long pending) {
        return DailyOrderStats.builder().statDate(LocalDate.now()).restaurantId(restaurantId)
                .totalOrders(4).cancelledOrders(1).deliveredOrders(1).pendingOrders(pending)
                .totalRevenue(BigDecimal.TEN).avgOrderValue(BigDecimal.TEN).build();
    }
}
