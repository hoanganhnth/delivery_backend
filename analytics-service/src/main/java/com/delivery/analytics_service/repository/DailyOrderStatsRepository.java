package com.delivery.analytics_service.repository;

import com.delivery.analytics_service.entity.DailyOrderStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyOrderStatsRepository extends JpaRepository<DailyOrderStats, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO daily_order_stats (
                stat_date, restaurant_id, total_orders, delivered_orders,
                cancelled_orders, pending_orders, total_revenue,
                total_shipping_fee, total_discount, avg_order_value, new_customers
            ) VALUES (:date, :restaurantId, 1, 0, 0, 1, 0, 0, 0, 0, 0)
            ON CONFLICT (stat_date, restaurant_id) DO UPDATE SET
                total_orders = daily_order_stats.total_orders + 1,
                pending_orders = daily_order_stats.pending_orders + 1
            """, nativeQuery = true)
    int incrementCreatedPostgres(@Param("date") LocalDate date,
                                 @Param("restaurantId") Long restaurantId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO daily_order_stats (
                stat_date, restaurant_id, total_orders, delivered_orders,
                cancelled_orders, pending_orders, total_revenue,
                total_shipping_fee, total_discount, avg_order_value, new_customers
            ) VALUES (:date, :restaurantId, 0, 1, 0, 0, :amount, 0, 0, :amount, 0)
            ON CONFLICT (stat_date, restaurant_id) DO UPDATE SET
                delivered_orders = daily_order_stats.delivered_orders + 1,
                pending_orders = GREATEST(daily_order_stats.pending_orders - 1, 0),
                total_revenue = COALESCE(daily_order_stats.total_revenue, 0) + :amount,
                avg_order_value = ROUND(
                    (COALESCE(daily_order_stats.total_revenue, 0) + :amount)
                    / (daily_order_stats.delivered_orders + 1), 0)
            """, nativeQuery = true)
    int incrementDeliveredPostgres(@Param("date") LocalDate date,
                                   @Param("restaurantId") Long restaurantId,
                                   @Param("amount") BigDecimal amount);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO daily_order_stats (
                stat_date, restaurant_id, total_orders, delivered_orders,
                cancelled_orders, pending_orders, total_revenue,
                total_shipping_fee, total_discount, avg_order_value, new_customers
            ) VALUES (:date, :restaurantId, 0, 0, 1, 0, 0, 0, 0, 0, 0)
            ON CONFLICT (stat_date, restaurant_id) DO UPDATE SET
                cancelled_orders = daily_order_stats.cancelled_orders + 1,
                pending_orders = GREATEST(daily_order_stats.pending_orders - 1, 0)
            """, nativeQuery = true)
    int incrementCancelledPostgres(@Param("date") LocalDate date,
                                   @Param("restaurantId") Long restaurantId);

    Optional<DailyOrderStats> findByStatDateAndRestaurantId(LocalDate statDate, Long restaurantId);

    /**
     * Platform stats: restaurantId IS NULL
     */
    Optional<DailyOrderStats> findByStatDateAndRestaurantIdIsNull(LocalDate statDate);

    // ==================== MONTHLY AGGREGATION ====================

    /** Thống kê theo tháng cho platform (Admin) */
    @Query("SELECT MONTH(d.statDate), SUM(d.totalOrders), SUM(d.deliveredOrders), SUM(d.cancelledOrders), SUM(d.totalRevenue) " +
           "FROM DailyOrderStats d WHERE d.restaurantId IS NULL AND YEAR(d.statDate) = :year " +
           "GROUP BY MONTH(d.statDate) ORDER BY MONTH(d.statDate)")
    List<Object[]> monthlyPlatformStats(@Param("year") int year);

    /** Thống kê theo tháng cho 1 nhà hàng */
    @Query("SELECT MONTH(d.statDate), SUM(d.totalOrders), SUM(d.deliveredOrders), SUM(d.cancelledOrders), SUM(d.totalRevenue) " +
           "FROM DailyOrderStats d WHERE d.restaurantId = :restaurantId AND YEAR(d.statDate) = :year " +
           "GROUP BY MONTH(d.statDate) ORDER BY MONTH(d.statDate)")
    List<Object[]> monthlyRestaurantStats(@Param("restaurantId") Long restaurantId, @Param("year") int year);

    // ==================== YEARLY AGGREGATION ====================

    @Query("SELECT YEAR(d.statDate), SUM(d.totalOrders), SUM(d.deliveredOrders), SUM(d.cancelledOrders), SUM(d.totalRevenue) " +
           "FROM DailyOrderStats d WHERE d.restaurantId IS NULL " +
           "GROUP BY YEAR(d.statDate) ORDER BY YEAR(d.statDate)")
    List<Object[]> yearlyPlatformStats();

    @Query("SELECT YEAR(d.statDate), SUM(d.totalOrders), SUM(d.deliveredOrders), SUM(d.cancelledOrders), SUM(d.totalRevenue) " +
           "FROM DailyOrderStats d WHERE d.restaurantId = :restaurantId " +
           "GROUP BY YEAR(d.statDate) ORDER BY YEAR(d.statDate)")
    List<Object[]> yearlyRestaurantStats(@Param("restaurantId") Long restaurantId);

    // ==================== OVERVIEW TOTALS ====================

    @Query("SELECT COALESCE(SUM(d.totalOrders), 0), COALESCE(SUM(d.deliveredOrders), 0), " +
           "COALESCE(SUM(d.cancelledOrders), 0), COALESCE(SUM(d.pendingOrders), 0), " +
           "COALESCE(SUM(d.totalRevenue), 0) " +
           "FROM DailyOrderStats d WHERE d.restaurantId IS NULL")
    Object[] platformOverviewTotals();

    @Query("SELECT COALESCE(SUM(d.totalOrders), 0), COALESCE(SUM(d.deliveredOrders), 0), " +
           "COALESCE(SUM(d.cancelledOrders), 0), COALESCE(SUM(d.pendingOrders), 0), " +
           "COALESCE(SUM(d.totalRevenue), 0) " +
           "FROM DailyOrderStats d WHERE d.restaurantId = :restaurantId")
    Object[] restaurantOverviewTotals(@Param("restaurantId") Long restaurantId);

    // ==================== TOP RESTAURANTS ====================

    @Query("SELECT d.restaurantId, SUM(d.totalOrders), SUM(d.totalRevenue) " +
           "FROM DailyOrderStats d WHERE d.restaurantId IS NOT NULL " +
           "GROUP BY d.restaurantId ORDER BY SUM(d.totalRevenue) DESC")
    List<Object[]> topRestaurantsByRevenue();

}
