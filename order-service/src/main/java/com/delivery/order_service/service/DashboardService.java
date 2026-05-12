package com.delivery.order_service.service;

import com.delivery.order_service.dto.response.DashboardStats;
import com.delivery.order_service.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service xử lý thống kê cho Dashboard
 * Hỗ trợ cả Admin (platform-wide) và Restaurant (per-restaurant)
 */
@Service
public class DashboardService {

    private final OrderRepository orderRepository;

    public DashboardService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // ==================== ADMIN DASHBOARD ====================

    /**
     * Lấy thống kê tổng quan Admin (toàn bộ platform)
     */
    public DashboardStats.AdminDashboardResponse getAdminDashboard(String period, Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear();

        DashboardStats.OverviewStats overview = buildAdminOverview();
        List<DashboardStats.TimeSeriesPoint> revenueSeries;
        List<DashboardStats.TimeSeriesPoint> orderSeries;

        switch (period != null ? period : "month") {
            case "quarter":
                revenueSeries = buildQuarterlyTimeSeries(orderRepository.monthlyStatsByYear(targetYear));
                orderSeries = revenueSeries; // Same data source
                break;
            case "year":
                revenueSeries = buildYearlyTimeSeries(orderRepository.yearlyStats());
                orderSeries = revenueSeries;
                break;
            default: // month
                revenueSeries = buildMonthlyTimeSeries(orderRepository.monthlyStatsByYear(targetYear));
                orderSeries = revenueSeries;
        }

        List<DashboardStats.OrderStatusBreakdown> statusBreakdown = buildStatusBreakdown(
            orderRepository.countAllByStatus()
        );

        List<DashboardStats.TopRestaurant> topRestaurants = buildTopRestaurants(
            orderRepository.topRestaurantsByRevenue()
        );

        return DashboardStats.AdminDashboardResponse.builder()
                .overview(overview)
                .revenueTimeSeries(revenueSeries)
                .orderTimeSeries(orderSeries)
                .statusBreakdown(statusBreakdown)
                .topRestaurants(topRestaurants)
                .build();
    }

    // ==================== RESTAURANT DASHBOARD ====================

    /**
     * Lấy thống kê cho 1 nhà hàng
     */
    public DashboardStats.RestaurantDashboardResponse getRestaurantDashboard(
            Long restaurantId, String period, Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear();

        DashboardStats.OverviewStats overview = buildRestaurantOverview(restaurantId);
        List<DashboardStats.TimeSeriesPoint> revenueSeries;
        List<DashboardStats.TimeSeriesPoint> orderSeries;

        switch (period != null ? period : "month") {
            case "quarter":
                revenueSeries = buildQuarterlyTimeSeries(
                    orderRepository.monthlyStatsByRestaurantAndYear(restaurantId, targetYear)
                );
                orderSeries = revenueSeries;
                break;
            case "year":
                revenueSeries = buildYearlyTimeSeries(
                    orderRepository.yearlyStatsByRestaurant(restaurantId)
                );
                orderSeries = revenueSeries;
                break;
            default: // month
                revenueSeries = buildMonthlyTimeSeries(
                    orderRepository.monthlyStatsByRestaurantAndYear(restaurantId, targetYear)
                );
                orderSeries = revenueSeries;
        }

        List<DashboardStats.OrderStatusBreakdown> statusBreakdown = buildStatusBreakdown(
            orderRepository.countByRestaurantIdGroupByStatus(restaurantId)
        );

        List<DashboardStats.TopMenuItem> topMenuItems = buildTopMenuItems(
            orderRepository.topMenuItemsByRestaurant(restaurantId)
        );

        return DashboardStats.RestaurantDashboardResponse.builder()
                .overview(overview)
                .revenueTimeSeries(revenueSeries)
                .orderTimeSeries(orderSeries)
                .statusBreakdown(statusBreakdown)
                .topMenuItems(topMenuItems)
                .build();
    }

    // ==================== PRIVATE BUILDERS ====================

    private DashboardStats.OverviewStats buildAdminOverview() {
        long totalOrders = orderRepository.count();
        BigDecimal totalRevenue = orderRepository.sumTotalRevenueDelivered();
        long delivered = orderRepository.countByStatus("DELIVERED");
        long cancelled = orderRepository.countByStatus("CANCELLED");
        long pending = orderRepository.countByStatus("PENDING");
        long processing = totalOrders - delivered - cancelled - pending;

        BigDecimal avgOrderValue = totalOrders > 0
            ? totalRevenue.divide(BigDecimal.valueOf(Math.max(delivered, 1)), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        double deliveryRate = totalOrders > 0
            ? (double) delivered / totalOrders * 100
            : 0;

        return DashboardStats.OverviewStats.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .deliveredOrders(delivered)
                .cancelledOrders(cancelled)
                .pendingOrders(pending)
                .processingOrders(processing)
                .avgOrderValue(avgOrderValue)
                .deliveryRate(Math.round(deliveryRate * 10.0) / 10.0)
                .build();
    }

    private DashboardStats.OverviewStats buildRestaurantOverview(Long restaurantId) {
        long totalOrders = orderRepository.countByRestaurantId(restaurantId);
        BigDecimal totalRevenue = orderRepository.sumRevenueByRestaurant(restaurantId);
        long delivered = orderRepository.countByRestaurantIdAndStatus(restaurantId, "DELIVERED");
        long cancelled = orderRepository.countByRestaurantIdAndStatus(restaurantId, "CANCELLED");
        long pending = orderRepository.countByRestaurantIdAndStatus(restaurantId, "PENDING");
        long processing = totalOrders - delivered - cancelled - pending;

        BigDecimal avgOrderValue = delivered > 0
            ? totalRevenue.divide(BigDecimal.valueOf(delivered), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        double deliveryRate = totalOrders > 0
            ? (double) delivered / totalOrders * 100
            : 0;

        return DashboardStats.OverviewStats.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .deliveredOrders(delivered)
                .cancelledOrders(cancelled)
                .pendingOrders(pending)
                .processingOrders(processing)
                .avgOrderValue(avgOrderValue)
                .deliveryRate(Math.round(deliveryRate * 10.0) / 10.0)
                .build();
    }

    private List<DashboardStats.TimeSeriesPoint> buildMonthlyTimeSeries(List<Object[]> rawData) {
        String[] monthLabels = {"T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10", "T11", "T12"};
        Map<Integer, Object[]> dataMap = rawData.stream()
            .collect(Collectors.toMap(
                row -> ((Number) row[0]).intValue(),
                row -> row
            ));

        List<DashboardStats.TimeSeriesPoint> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Object[] row = dataMap.get(m);
            result.add(DashboardStats.TimeSeriesPoint.builder()
                .label(monthLabels[m - 1])
                .orderCount(row != null ? ((Number) row[1]).longValue() : 0)
                .revenue(row != null ? (BigDecimal) row[2] : BigDecimal.ZERO)
                .build());
        }
        return result;
    }

    private List<DashboardStats.TimeSeriesPoint> buildQuarterlyTimeSeries(List<Object[]> monthlyData) {
        // Aggregate monthly data into quarters
        List<DashboardStats.TimeSeriesPoint> monthly = buildMonthlyTimeSeries(monthlyData);
        List<DashboardStats.TimeSeriesPoint> quarters = new ArrayList<>();
        for (int q = 0; q < 4; q++) {
            long orders = 0;
            BigDecimal rev = BigDecimal.ZERO;
            for (int m = q * 3; m < (q + 1) * 3 && m < monthly.size(); m++) {
                orders += monthly.get(m).getOrderCount();
                rev = rev.add(monthly.get(m).getRevenue());
            }
            quarters.add(DashboardStats.TimeSeriesPoint.builder()
                .label("Q" + (q + 1))
                .orderCount(orders)
                .revenue(rev)
                .build());
        }
        return quarters;
    }

    private List<DashboardStats.TimeSeriesPoint> buildYearlyTimeSeries(List<Object[]> rawData) {
        return rawData.stream()
            .map(row -> DashboardStats.TimeSeriesPoint.builder()
                .label(String.valueOf(((Number) row[0]).intValue()))
                .orderCount(((Number) row[1]).longValue())
                .revenue((BigDecimal) row[2])
                .build())
            .collect(Collectors.toList());
    }

    private List<DashboardStats.OrderStatusBreakdown> buildStatusBreakdown(List<Object[]> rawData) {
        return rawData.stream()
            .map(row -> DashboardStats.OrderStatusBreakdown.builder()
                .status((String) row[0])
                .count(((Number) row[1]).longValue())
                .build())
            .collect(Collectors.toList());
    }

    private List<DashboardStats.TopRestaurant> buildTopRestaurants(List<Object[]> rawData) {
        return rawData.stream()
            .limit(10) // Top 10
            .map(row -> DashboardStats.TopRestaurant.builder()
                .restaurantId(((Number) row[0]).longValue())
                .restaurantName((String) row[1])
                .orderCount(((Number) row[2]).longValue())
                .revenue((BigDecimal) row[3])
                .build())
            .collect(Collectors.toList());
    }

    private List<DashboardStats.TopMenuItem> buildTopMenuItems(List<Object[]> rawData) {
        return rawData.stream()
            .limit(10) // Top 10
            .map(row -> DashboardStats.TopMenuItem.builder()
                .itemName((String) row[0])
                .quantity(((Number) row[1]).longValue())
                .revenue((BigDecimal) row[2])
                .build())
            .collect(Collectors.toList());
    }
}
