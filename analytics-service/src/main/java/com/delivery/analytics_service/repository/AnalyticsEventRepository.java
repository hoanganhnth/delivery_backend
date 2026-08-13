package com.delivery.analytics_service.repository;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    boolean existsByDeduplicationKey(String deduplicationKey);

    Optional<AnalyticsEvent> findByDeduplicationKey(String deduplicationKey);

    /** Atomic cross-replica receipt claim on PostgreSQL. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO analytics_events (
                deduplication_key, event_type, event_time, order_id, user_id,
                restaurant_id, restaurant_name, amount, order_status,
                payment_method, raw_payload, payload_fingerprint
            ) VALUES (
                :deduplicationKey, :eventType, CURRENT_TIMESTAMP, :orderId, :userId,
                :restaurantId, :restaurantName, :amount, :orderStatus,
                :paymentMethod, :rawPayload, :payloadFingerprint
            ) ON CONFLICT (deduplication_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsentPostgres(
            @Param("deduplicationKey") String deduplicationKey,
            @Param("eventType") String eventType,
            @Param("orderId") Long orderId,
            @Param("userId") Long userId,
            @Param("restaurantId") Long restaurantId,
            @Param("restaurantName") String restaurantName,
            @Param("amount") java.math.BigDecimal amount,
            @Param("orderStatus") String orderStatus,
            @Param("paymentMethod") String paymentMethod,
            @Param("rawPayload") String rawPayload,
            @Param("payloadFingerprint") String payloadFingerprint);

    /**
     * Lấy tất cả events trong khoảng thời gian (cho Scheduled Job re-computation)
     */
    Page<AnalyticsEvent> findByEventTimeBetween(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

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
