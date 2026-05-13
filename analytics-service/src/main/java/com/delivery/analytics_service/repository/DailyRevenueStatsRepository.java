package com.delivery.analytics_service.repository;

import com.delivery.analytics_service.entity.DailyRevenueStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyRevenueStatsRepository extends JpaRepository<DailyRevenueStats, Long> {

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
