package com.delivery.analytics_service.service;
import com.delivery.analytics.application.DashboardService;
import com.delivery.analytics.applicationapi.*;
import com.delivery.analytics.domain.DashboardValues.*;
import com.delivery.analytics_service.dto.DashboardResponse;
import com.delivery.analytics_service.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
@Service
public class DashboardQueryService {
    private final DashboardUseCase queries;
    public DashboardQueryService(DailyOrderStatsRepository orders, DailyRevenueStatsRepository revenue,
                                 AnalyticsEventRepository events) {
        queries=new DashboardService(new DashboardReadPort() {
            public Totals totals(Scope scope) {
                Object[] row=scope.platform()?orders.platformOverviewTotals():orders.restaurantOverviewTotals(scope.restaurantId());
                return row==null?null:new Totals(((Number)row[0]).longValue(),((Number)row[1]).longValue(),
                        ((Number)row[2]).longValue(),((Number)row[3]).longValue(),(BigDecimal)row[4]);
            }
            public List<SeriesRow> monthly(Scope scope,int year) {
                return series(scope.platform()?orders.monthlyPlatformStats(year):orders.monthlyRestaurantStats(scope.restaurantId(),year));
            }
            public List<SeriesRow> yearly(Scope scope) {
                return series(scope.platform()?orders.yearlyPlatformStats():orders.yearlyRestaurantStats(scope.restaurantId()));
            }
            public List<TopRestaurant> topRestaurants(int limit) {
                return events.topRestaurantsByDeliveredRevenue(PageRequest.of(0,limit)).stream()
                        .map(r -> new TopRestaurant(((Number)r[0]).longValue(),(String)r[1],((Number)r[2]).longValue(),(BigDecimal)r[3]))
                        .collect(java.util.stream.Collectors.toList());
            }
        },LocalDate::now);
    }
    private static List<SeriesRow> series(List<Object[]> rows) {
        return rows.stream().map(r -> new SeriesRow(((Number)r[0]).intValue(),((Number)r[1]).longValue(),(BigDecimal)r[4]))
                .collect(java.util.stream.Collectors.toList());
    }
    public DashboardResponse.AdminDashboard getAdminDashboard(String period,Integer year) {
        var data=queries.admin(period,year);
        var series=points(data.series());
        return DashboardResponse.AdminDashboard.builder().overview(overview(data.overview()))
                .revenueTimeSeries(series).orderTimeSeries(series).statusBreakdown(statuses(data.statuses()))
                .topRestaurants(data.topRestaurants().stream().map(r -> DashboardResponse.TopRestaurant.builder()
                        .restaurantId(r.restaurantId()).restaurantName(r.restaurantName()).orderCount(r.orderCount())
                        .revenue(r.revenue()).build()).collect(java.util.stream.Collectors.toList())).build();
    }
    public DashboardResponse.RestaurantDashboard getRestaurantDashboard(Long id,String period,Integer year) {
        var data=queries.restaurant(id,period,year);
        var series=points(data.series());
        return DashboardResponse.RestaurantDashboard.builder().overview(overview(data.overview()))
                .revenueTimeSeries(series).orderTimeSeries(series).statusBreakdown(statuses(data.statuses())).build();
    }
    private static DashboardResponse.OverviewStats overview(Overview r) {
        return DashboardResponse.OverviewStats.builder().totalOrders(r.total()).deliveredOrders(r.delivered())
                .cancelledOrders(r.cancelled()).pendingOrders(r.pending()).processingOrders(r.processing())
                .totalRevenue(r.revenue()).avgOrderValue(r.average()).deliveryRate(r.deliveryRate()).build();
    }
    private static List<DashboardResponse.TimeSeriesPoint> points(List<Point> rows) {
        return rows.stream().map(r -> DashboardResponse.TimeSeriesPoint.builder().label(r.label())
                .orderCount(r.orderCount()).revenue(r.revenue()).build()).collect(java.util.stream.Collectors.toList());
    }
    private static List<DashboardResponse.StatusBreakdown> statuses(List<Status> rows) {
        return rows.stream().map(r -> new DashboardResponse.StatusBreakdown(r.status(),r.count())).collect(java.util.stream.Collectors.toList());
    }
}
