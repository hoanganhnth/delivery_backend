package com.delivery.order_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO chứa dữ liệu thống kê dashboard
 */
public class DashboardStats {

    /**
     * Thống kê tổng quan nhanh
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewStats {
        private long totalOrders;
        private BigDecimal totalRevenue;
        private long deliveredOrders;
        private long cancelledOrders;
        private long pendingOrders;
        private long processingOrders;
        private BigDecimal avgOrderValue;
        private double deliveryRate; // % đơn giao thành công
    }

    /**
     * Thống kê theo từng mốc thời gian (tháng, quý, năm)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesPoint {
        private String label;       // "T1", "Q1", "2024"
        private long orderCount;
        private BigDecimal revenue;
    }

    /**
     * Thống kê theo trạng thái đơn hàng
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusBreakdown {
        private String status;
        private long count;
    }

    /**
     * Top nhà hàng theo doanh thu/đơn hàng
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopRestaurant {
        private Long restaurantId;
        private String restaurantName;
        private long orderCount;
        private BigDecimal revenue;
    }

    /**
     * Top món ăn bán chạy (cho restaurant)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopMenuItem {
        private String itemName;
        private long quantity;
        private BigDecimal revenue;
    }

    /**
     * Response tổng hợp cho Admin Dashboard
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDashboardResponse {
        private OverviewStats overview;
        private List<TimeSeriesPoint> revenueTimeSeries;
        private List<TimeSeriesPoint> orderTimeSeries;
        private List<OrderStatusBreakdown> statusBreakdown;
        private List<TopRestaurant> topRestaurants;
    }

    /**
     * Response tổng hợp cho Restaurant Dashboard
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestaurantDashboardResponse {
        private OverviewStats overview;
        private List<TimeSeriesPoint> revenueTimeSeries;
        private List<TimeSeriesPoint> orderTimeSeries;
        private List<OrderStatusBreakdown> statusBreakdown;
        private List<TopMenuItem> topMenuItems;
    }
}
