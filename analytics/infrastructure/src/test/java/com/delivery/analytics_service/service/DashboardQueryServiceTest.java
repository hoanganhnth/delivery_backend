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
    @Test
    void yearlyQueriesPreserveRepositoryOrderAndSharedSeriesInstanceForBothScopes() {
        var orders = mock(DailyOrderStatsRepository.class);
        var events = mock(AnalyticsEventRepository.class);
        var revenue = mock(DailyRevenueStatsRepository.class);
        var service = new DashboardQueryService(orders, revenue, events);
        var rows = List.<Object[]>of(new Object[]{2026, 3L, 0L, 0L, new BigDecimal("11")},
                new Object[]{2024, 2L, 0L, 0L, BigDecimal.TEN});
        when(orders.yearlyPlatformStats()).thenReturn(rows);
        when(orders.yearlyRestaurantStats(7L)).thenReturn(rows);
        when(orders.restaurantOverviewTotals(7L)).thenReturn(new Object[]{1L, 2L, 3L, 4L, new BigDecimal("11")});
        var admin = service.getAdminDashboard("year", 1900);
        var restaurant = service.getRestaurantDashboard(7L, "year", 2200);
        assertThat(admin.getOverview().getDeliveryRate()).isZero();
        assertThat(admin.getStatusBreakdown()).isEmpty();
        assertThat(admin.getOrderTimeSeries()).isSameAs(admin.getRevenueTimeSeries());
        assertThat(restaurant.getOrderTimeSeries()).isSameAs(restaurant.getRevenueTimeSeries());
        assertThat(restaurant.getOrderTimeSeries()).extracting(DashboardResponse.TimeSeriesPoint::getLabel)
                .containsExactly("2026", "2024");
        assertThat(restaurant.getOverview().getProcessingOrders()).isEqualTo(-8);
        assertThat(restaurant.getOverview().getAvgOrderValue()).isEqualByComparingTo("6");
        assertThat(restaurant.getStatusBreakdown()).extracting(DashboardResponse.StatusBreakdown::getStatus)
                .containsExactly("DELIVERED", "CANCELLED", "PENDING");
        verifyNoInteractions(revenue);
        verify(orders, times(2)).platformOverviewTotals();
        verify(orders, times(2)).restaurantOverviewTotals(7L);
    }

    @Test
    void quarterAdminAndMonthlyFallbackRestaurantUseCurrentYearWhenAbsent() {
        var orders = mock(DailyOrderStatsRepository.class);
        var events = mock(AnalyticsEventRepository.class);
        var service = new DashboardQueryService(orders, mock(DailyRevenueStatsRepository.class), events);
        int year = java.time.LocalDate.now().getYear();
        when(orders.monthlyPlatformStats(year)).thenReturn(List.<Object[]>of(new Object[]{4, 2L, 0L, 0L, BigDecimal.TEN}));
        when(orders.monthlyRestaurantStats(null, year)).thenReturn(List.of());
        var admin = service.getAdminDashboard("quarter", null);
        assertThat(admin.getRevenueTimeSeries().get(1).getRevenue()).isEqualByComparingTo("10");
        var restaurant = service.getRestaurantDashboard(null, " YEAR ", null);
        assertThat(restaurant.getRevenueTimeSeries()).hasSize(12);
        verify(orders, times(2)).restaurantOverviewTotals(null);
        verify(orders).monthlyRestaurantStats(null, year);
        verify(events).topRestaurantsByDeliveredRevenue(org.springframework.data.domain.PageRequest.of(0, 10));
    }
}
