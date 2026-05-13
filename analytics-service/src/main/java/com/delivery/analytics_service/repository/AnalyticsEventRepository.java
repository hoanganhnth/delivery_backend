package com.delivery.analytics_service.repository;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    /**
     * Lấy tất cả events trong khoảng thời gian (cho Scheduled Job re-computation)
     */
    List<AnalyticsEvent> findByEventTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Đếm events theo loại trong khoảng thời gian
     */
    @Query("SELECT e.eventType, COUNT(e) FROM AnalyticsEvent e " +
           "WHERE e.eventTime BETWEEN :start AND :end GROUP BY e.eventType")
    List<Object[]> countByEventTypeAndTimeBetween(
        @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Top nhà hàng theo số đơn (từ raw events)
     */
    @Query("SELECT e.restaurantId, e.restaurantName, COUNT(e), COALESCE(SUM(e.amount), 0) " +
           "FROM AnalyticsEvent e WHERE e.eventType = 'ORDER_DELIVERED' AND e.restaurantId IS NOT NULL " +
           "GROUP BY e.restaurantId, e.restaurantName ORDER BY SUM(e.amount) DESC")
    List<Object[]> topRestaurantsByDeliveredRevenue(org.springframework.data.domain.Pageable pageable);
}
