package com.delivery.analytics_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bảng thống kê giao dịch thanh toán theo ngày
 * Dữ liệu từ settlement-service qua Kafka
 */
@Entity
@Table(name = "daily_revenue_stats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"stat_date", "restaurant_id"})
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class DailyRevenueStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    /** Tổng doanh thu thanh toán thành công */
    @Column(name = "total_payment_amount", precision = 15, scale = 2)
    private BigDecimal totalPaymentAmount;

    /** Số giao dịch thành công */
    @Column(name = "successful_payments")
    private long successfulPayments;

    /** Số giao dịch thất bại */
    @Column(name = "failed_payments")
    private long failedPayments;

    /** Tổng tiền rút (withdrawal) */
    @Column(name = "total_withdrawals", precision = 15, scale = 2)
    private BigDecimal totalWithdrawals;

    /** Phí nền tảng thu được */
    @Column(name = "platform_fee", precision = 15, scale = 2)
    private BigDecimal platformFee;
}
