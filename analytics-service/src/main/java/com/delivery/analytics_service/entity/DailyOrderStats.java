package com.delivery.analytics_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bảng thống kê đơn hàng theo ngày — được tạo bởi Kafka event listener & Scheduled Job
 * 
 * Mỗi dòng = 1 ngày + 1 restaurantId (hoặc null cho platform-wide)
 * Dữ liệu được cập nhật real-time khi nhận event từ Kafka
 * và được chuẩn hóa lại bởi Scheduled Job hàng đêm
 */
@Entity
@Table(name = "daily_order_stats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"stat_date", "restaurant_id"})
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class DailyOrderStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    /**
     * null = thống kê toàn bộ platform (Admin)
     * non-null = thống kê cho 1 nhà hàng
     */
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "total_orders")
    private long totalOrders;

    @Column(name = "delivered_orders")
    private long deliveredOrders;

    @Column(name = "cancelled_orders")
    private long cancelledOrders;

    @Column(name = "pending_orders")
    private long pendingOrders;

    @Column(name = "total_revenue", precision = 15, scale = 2)
    private BigDecimal totalRevenue;

    @Column(name = "total_shipping_fee", precision = 15, scale = 2)
    private BigDecimal totalShippingFee;

    @Column(name = "total_discount", precision = 15, scale = 2)
    private BigDecimal totalDiscount;

    @Column(name = "avg_order_value", precision = 12, scale = 2)
    private BigDecimal avgOrderValue;

    @Column(name = "new_customers")
    private long newCustomers;
}
