package com.delivery.analytics_service.service;

import com.delivery.analytics_service.dto.DashboardResponse;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service đọc dữ liệu thống kê đã được pre-aggregate
 * Trả về DTO cho REST API — Dashboard Frontend
 *
 * Dữ liệu được lấy từ bảng daily_order_stats & daily_revenue_stats
 * (đã được tính sẵn bởi Kafka event processing + Scheduled Job)
 * → Tốc độ query cực nhanh vì chỉ SELECT trên bảng nhỏ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardQueryService {

    private final DailyOrderStatsRepository orderStatsRepo;
    private final DailyRevenueStatsRepository revenueStatsRepo;
    private final AnalyticsEventRepository eventRepo;

    // ==================== ADMIN DASHBOARD ====================

    public DashboardResponse.AdminDashboard getAdminDashboard(String period, Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear();

        // Overview
        DashboardResponse.OverviewStats overview = buildPlatformOverview();

        // Time series
        List<DashboardResponse.TimeSeriesPoint> timeSeries;
        switch (period != null ? period : "month") {
            case "quarter":
                timeSeries = buildQuarterlyFromMonthly(orderStatsRepo.monthlyPlatformStats(targetYear));
                break;
            case "year":
                timeSeries = buildYearlySeries(orderStatsRepo.yearlyPlatformStats());
                break;
            default:
                timeSeries = buildMonthlySeries(orderStatsRepo.monthlyPlatformStats(targetYear));
        }

        // Status breakdown
        List<DashboardResponse.StatusBreakdown> statusBreakdown = buildPlatformStatusBreakdown();

        // Top restaurants
        List<DashboardResponse.TopRestaurant> topRestaurants = buildTopRestaurants();

        return DashboardResponse.AdminDashboard.builder()
                .overview(overview)
                .revenueTimeSeries(timeSeries)
                .orderTimeSeries(timeSeries) // same data source
                .statusBreakdown(statusBreakdown)
                .topRestaurants(topRestaurants)
                .build();
    }

    // ==================== RESTAURANT DASHBOARD ====================

    public DashboardResponse.RestaurantDashboard getRestaurantDashboard(Long restaurantId, String period, Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear();

        DashboardResponse.OverviewStats overview = buildRestaurantOverview(restaurantId);

        List<DashboardResponse.TimeSeriesPoint> timeSeries;
        switch (period != null ? period : "month") {
            case "quarter":
                timeSeries = buildQuarterlyFromMonthly(orderStatsRepo.monthlyRestaurantStats(restaurantId, targetYear));
                break;
            case "year":
                timeSeries = buildYearlySeries(orderStatsRepo.yearlyRestaurantStats(restaurantId));
                break;
            default:
                timeSeries = buildMonthlySeries(orderStatsRepo.monthlyRestaurantStats(restaurantId, targetYear));
        }

        List<DashboardResponse.StatusBreakdown> statusBreakdown = buildRestaurantStatusBreakdown(restaurantId);

        return DashboardResponse.RestaurantDashboard.builder()
                .overview(overview)
                .revenueTimeSeries(timeSeries)
                .orderTimeSeries(timeSeries)
                .statusBreakdown(statusBreakdown)
                .topMenuItems(List.of()) // TODO: implement when menu item data is available
                .build();
    }

    // ==================== PRIVATE BUILDERS ====================

    private DashboardResponse.OverviewStats buildPlatformOverview() {
        Object[] row = orderStatsRepo.platformOverviewTotals();
        if (row == null) {
            return DashboardResponse.OverviewStats.builder()
                    .totalOrders(0).deliveredOrders(0).cancelledOrders(0).pendingOrders(0)
                    .totalRevenue(BigDecimal.ZERO).avgOrderValue(BigDecimal.ZERO).deliveryRate(0)
                    .build();
        }
        long total = ((Number) row[0]).longValue();
        long delivered = ((Number) row[1]).longValue();
        long cancelled = ((Number) row[2]).longValue();
        long pending = ((Number) row[3]).longValue();
        BigDecimal revenue = (BigDecimal) row[4];
        long processing = total - delivered - cancelled - pending;
        BigDecimal avg = delivered > 0
                ? revenue.divide(BigDecimal.valueOf(delivered), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        double rate = total > 0 ? Math.round((double) delivered / total * 1000.0) / 10.0 : 0;

        return DashboardResponse.OverviewStats.builder()
                .totalOrders(total).deliveredOrders(delivered).cancelledOrders(cancelled)
                .pendingOrders(pending).processingOrders(processing)
                .totalRevenue(revenue).avgOrderValue(avg).deliveryRate(rate)
                .build();
    }

    private DashboardResponse.OverviewStats buildRestaurantOverview(Long restaurantId) {
        Object[] row = orderStatsRepo.restaurantOverviewTotals(restaurantId);
        if (row == null) {
            return DashboardResponse.OverviewStats.builder()
                    .totalOrders(0).deliveredOrders(0).cancelledOrders(0).pendingOrders(0)
                    .totalRevenue(BigDecimal.ZERO).avgOrderValue(BigDecimal.ZERO).deliveryRate(0)
                    .build();
        }
        long total = ((Number) row[0]).longValue();
        long delivered = ((Number) row[1]).longValue();
        long cancelled = ((Number) row[2]).longValue();
        long pending = ((Number) row[3]).longValue();
        BigDecimal revenue = (BigDecimal) row[4];
        long processing = total - delivered - cancelled - pending;
        BigDecimal avg = delivered > 0
                ? revenue.divide(BigDecimal.valueOf(delivered), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        double rate = total > 0 ? Math.round((double) delivered / total * 1000.0) / 10.0 : 0;

        return DashboardResponse.OverviewStats.builder()
                .totalOrders(total).deliveredOrders(delivered).cancelledOrders(cancelled)
                .pendingOrders(pending).processingOrders(processing)
                .totalRevenue(revenue).avgOrderValue(avg).deliveryRate(rate)
                .build();
    }

    private List<DashboardResponse.TimeSeriesPoint> buildMonthlySeries(List<Object[]> rawData) {
        String[] labels = {"T1","T2","T3","T4","T5","T6","T7","T8","T9","T10","T11","T12"};
        Map<Integer, Object[]> map = rawData.stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).intValue(), r -> r));

        List<DashboardResponse.TimeSeriesPoint> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Object[] row = map.get(m);
            result.add(DashboardResponse.TimeSeriesPoint.builder()
                    .label(labels[m - 1])
                    .orderCount(row != null ? ((Number) row[1]).longValue() : 0)
                    .revenue(row != null ? (BigDecimal) row[4] : BigDecimal.ZERO)
                    .build());
        }
        return result;
    }

    private List<DashboardResponse.TimeSeriesPoint> buildQuarterlyFromMonthly(List<Object[]> monthlyData) {
        List<DashboardResponse.TimeSeriesPoint> monthly = buildMonthlySeries(monthlyData);
        List<DashboardResponse.TimeSeriesPoint> quarters = new ArrayList<>();
        for (int q = 0; q < 4; q++) {
            long orders = 0;
            BigDecimal rev = BigDecimal.ZERO;
            for (int m = q * 3; m < (q + 1) * 3 && m < monthly.size(); m++) {
                orders += monthly.get(m).getOrderCount();
                rev = rev.add(monthly.get(m).getRevenue());
            }
            quarters.add(DashboardResponse.TimeSeriesPoint.builder()
                    .label("Q" + (q + 1)).orderCount(orders).revenue(rev).build());
        }
        return quarters;
    }

    private List<DashboardResponse.TimeSeriesPoint> buildYearlySeries(List<Object[]> rawData) {
        return rawData.stream()
                .map(row -> DashboardResponse.TimeSeriesPoint.builder()
                        .label(String.valueOf(((Number) row[0]).intValue()))
                        .orderCount(((Number) row[1]).longValue())
                        .revenue((BigDecimal) row[4])
                        .build())
                .collect(Collectors.toList());
    }

    private List<DashboardResponse.StatusBreakdown> buildPlatformStatusBreakdown() {
        Object[] row = orderStatsRepo.platformOverviewTotals();
        if (row == null) return List.of();
        return List.of(
                new DashboardResponse.StatusBreakdown("DELIVERED", ((Number) row[1]).longValue()),
                new DashboardResponse.StatusBreakdown("CANCELLED", ((Number) row[2]).longValue()),
                new DashboardResponse.StatusBreakdown("PENDING", ((Number) row[3]).longValue())
        );
    }

    private List<DashboardResponse.StatusBreakdown> buildRestaurantStatusBreakdown(Long restaurantId) {
        Object[] row = orderStatsRepo.restaurantOverviewTotals(restaurantId);
        if (row == null) return List.of();
        return List.of(
                new DashboardResponse.StatusBreakdown("DELIVERED", ((Number) row[1]).longValue()),
                new DashboardResponse.StatusBreakdown("CANCELLED", ((Number) row[2]).longValue()),
                new DashboardResponse.StatusBreakdown("PENDING", ((Number) row[3]).longValue())
        );
    }

    private List<DashboardResponse.TopRestaurant> buildTopRestaurants() {
        // Sử dụng raw events để lấy top restaurants (có cả tên)
        List<Object[]> rows = eventRepo.topRestaurantsByDeliveredRevenue();
        return rows.stream()
                .limit(10)
                .map(row -> DashboardResponse.TopRestaurant.builder()
                        .restaurantId(((Number) row[0]).longValue())
                        .restaurantName((String) row[1])
                        .orderCount(((Number) row[2]).longValue())
                        .revenue((BigDecimal) row[3])
                        .build())
                .collect(Collectors.toList());
    }
}
