package com.delivery.analytics_service.repository;

import com.delivery.analytics_service.entity.DailyOrderStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyOrderStatsRepository extends JpaRepository<DailyOrderStats, Long> {

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

    /** Tất cả bản ghi trong khoảng ngày (cho scheduled job reconciliation) */
    List<DailyOrderStats> findByStatDateBetween(LocalDate start, LocalDate end);
}
