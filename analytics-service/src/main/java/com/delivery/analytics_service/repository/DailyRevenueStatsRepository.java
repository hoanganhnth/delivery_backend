package com.delivery.analytics_service.repository;

import com.delivery.analytics_service.entity.DailyRevenueStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyRevenueStatsRepository extends JpaRepository<DailyRevenueStats, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO daily_revenue_stats (
                stat_date, restaurant_id, total_payment_amount,
                successful_payments, failed_payments, total_withdrawals, platform_fee
            ) VALUES (:date, NULL, :amount, 1, 0, 0, 0)
            ON CONFLICT (stat_date, restaurant_id) DO UPDATE SET
                successful_payments = daily_revenue_stats.successful_payments + 1,
                total_payment_amount = COALESCE(daily_revenue_stats.total_payment_amount, 0) + :amount
            """, nativeQuery = true)
    int incrementPaymentCompletedPostgres(@Param("date") LocalDate date,
                                          @Param("amount") java.math.BigDecimal amount);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO daily_revenue_stats (
                stat_date, restaurant_id, total_payment_amount,
                successful_payments, failed_payments, total_withdrawals, platform_fee
            ) VALUES (:date, NULL, 0, 0, 1, 0, 0)
            ON CONFLICT (stat_date, restaurant_id) DO UPDATE SET
                failed_payments = daily_revenue_stats.failed_payments + 1
            """, nativeQuery = true)
    int incrementPaymentFailedPostgres(@Param("date") LocalDate date);

    Optional<DailyRevenueStats> findByStatDateAndRestaurantId(LocalDate statDate, Long restaurantId);

    Optional<DailyRevenueStats> findByStatDateAndRestaurantIdIsNull(LocalDate statDate);

    /** Monthly payment stats (platform) */
    @Query("SELECT MONTH(d.statDate), SUM(d.totalPaymentAmount), SUM(d.successfulPayments), SUM(d.failedPayments) " +
           "FROM DailyRevenueStats d WHERE d.restaurantId IS NULL AND YEAR(d.statDate) = :year " +
           "GROUP BY MONTH(d.statDate) ORDER BY MONTH(d.statDate)")
    List<Object[]> monthlyPlatformRevenueStats(@Param("year") int year);

    /** Monthly payment stats (restaurant) */
    @Query("SELECT MONTH(d.statDate), SUM(d.totalPaymentAmount), SUM(d.successfulPayments), SUM(d.failedPayments) " +
           "FROM DailyRevenueStats d WHERE d.restaurantId = :restaurantId AND YEAR(d.statDate) = :year " +
           "GROUP BY MONTH(d.statDate) ORDER BY MONTH(d.statDate)")
    List<Object[]> monthlyRestaurantRevenueStats(@Param("restaurantId") Long restaurantId, @Param("year") int year);
}
