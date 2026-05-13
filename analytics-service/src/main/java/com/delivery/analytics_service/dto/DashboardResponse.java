package com.delivery.analytics_service.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTOs cho Dashboard API
 * Tương thích với frontend đã thiết kế
 */
public class DashboardResponse {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OverviewStats {
        private long totalOrders;
        private BigDecimal totalRevenue;
        private long deliveredOrders;
        private long cancelledOrders;
        private long pendingOrders;
        private long processingOrders;
        private BigDecimal avgOrderValue;
        private double deliveryRate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TimeSeriesPoint {
        private String label;
        private long orderCount;
        private BigDecimal revenue;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class StatusBreakdown {
        private String status;
        private long count;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TopRestaurant {
        private Long restaurantId;
        private String restaurantName;
        private long orderCount;
        private BigDecimal revenue;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TopMenuItem {
        private String itemName;
        private long quantity;
        private BigDecimal revenue;
    }

    // ==================== AGGREGATE RESPONSES ====================

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdminDashboard {
        private OverviewStats overview;
        private List<TimeSeriesPoint> revenueTimeSeries;
        private List<TimeSeriesPoint> orderTimeSeries;
        private List<StatusBreakdown> statusBreakdown;
        private List<TopRestaurant> topRestaurants;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RestaurantDashboard {
        private OverviewStats overview;
        private List<TimeSeriesPoint> revenueTimeSeries;
        private List<TimeSeriesPoint> orderTimeSeries;
        private List<StatusBreakdown> statusBreakdown;
        private List<TopMenuItem> topMenuItems;
    }
}
