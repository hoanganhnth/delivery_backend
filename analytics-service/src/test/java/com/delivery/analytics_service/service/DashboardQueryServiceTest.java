package com.delivery.analytics_service.service;

import com.delivery.analytics_service.dto.DashboardResponse;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashboardQueryServiceTest {
    @Test
    void adminDashboardFillsMissingMonthsAndDerivesOverviewFromAggregates() {
        DailyOrderStatsRepository orders = mock(DailyOrderStatsRepository.class);
        AnalyticsEventRepository events = mock(AnalyticsEventRepository.class);
        DashboardQueryService service = new DashboardQueryService(orders,
                mock(DailyRevenueStatsRepository.class), events);
        when(orders.platformOverviewTotals()).thenReturn(new Object[]{10L, 6L, 2L, 1L, new BigDecimal("900")});
        when(orders.monthlyPlatformStats(2026)).thenReturn(List.<Object[]>of(
                new Object[]{1, 3L, 0L, 0L, new BigDecimal("300")}));
        when(events.topRestaurantsByDeliveredRevenue(any())).thenReturn(List.<Object[]>of(
                new Object[]{7L, "Bún bò", 6L, new BigDecimal("600")}));

        DashboardResponse.AdminDashboard dashboard = service.getAdminDashboard("month", 2026);

        assertThat(dashboard.getOverview().getProcessingOrders()).isEqualTo(1);
        assertThat(dashboard.getOverview().getAvgOrderValue()).isEqualByComparingTo("150");
        assertThat(dashboard.getOverview().getDeliveryRate()).isEqualTo(60.0);
        assertThat(dashboard.getRevenueTimeSeries()).hasSize(12);
        assertThat(dashboard.getRevenueTimeSeries().get(0).getLabel()).isEqualTo("T1");
        assertThat(dashboard.getRevenueTimeSeries().get(0).getOrderCount()).isEqualTo(3);
        assertThat(dashboard.getRevenueTimeSeries().get(1).getRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dashboard.getTopRestaurants()).singleElement().satisfies(top -> {
            assertThat(top.getRestaurantId()).isEqualTo(7L);
            assertThat(top.getRevenue()).isEqualByComparingTo("600");
        });
    }

    @Test
    void restaurantDashboardReturnsSafeEmptyOverviewAndQuarterlyZeroFill() {
        DailyOrderStatsRepository orders = mock(DailyOrderStatsRepository.class);
        DashboardQueryService service = new DashboardQueryService(orders,
                mock(DailyRevenueStatsRepository.class), mock(AnalyticsEventRepository.class));
        when(orders.restaurantOverviewTotals(9L)).thenReturn(null);
        when(orders.monthlyRestaurantStats(9L, 2026)).thenReturn(List.<Object[]>of(
                new Object[]{2, 4L, 0L, 0L, new BigDecimal("400")}));

        DashboardResponse.RestaurantDashboard dashboard = service.getRestaurantDashboard(9L, "quarter", 2026);

        assertThat(dashboard.getOverview().getTotalOrders()).isZero();
        assertThat(dashboard.getStatusBreakdown()).isEmpty();
        assertThat(dashboard.getOrderTimeSeries()).extracting(DashboardResponse.TimeSeriesPoint::getLabel)
                .containsExactly("Q1", "Q2", "Q3", "Q4");
        assertThat(dashboard.getOrderTimeSeries().get(0).getOrderCount()).isEqualTo(4);
        assertThat(dashboard.getOrderTimeSeries().get(0).getRevenue()).isEqualByComparingTo("400");
    }
}
