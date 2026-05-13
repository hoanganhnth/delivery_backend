package com.delivery.analytics_service.scheduler;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import com.delivery.analytics_service.entity.DailyOrderStats;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scheduled Job — Chạy hàng đêm lúc 00:05 để chuẩn hóa dữ liệu thống kê
 *
 * Cơ chế:
 * 1. Đọc tất cả raw events từ bảng analytics_events của ngày hôm qua
 * 2. Tính toán lại (re-compute) chính xác các chỉ số thống kê
 * 3. Ghi đè (upsert) vào bảng daily_order_stats
 *
 * Tại sao cần?
 * - Real-time update (từ Kafka listener) có thể bị miss event hoặc duplicate
 * - Scheduled Job là "source of truth" cuối cùng, đảm bảo dữ liệu chính xác 100%
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatsReconciliationJob {

    private final AnalyticsEventRepository eventRepo;
    private final DailyOrderStatsRepository orderStatsRepo;

    /**
     * Chạy lúc 00:05 mỗi ngày — tính lại thống kê cho ngày hôm qua
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void reconcileYesterdayStats() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("🔄 [Scheduler] Starting daily stats reconciliation for: {}", yesterday);

        try {
            reconcileDate(yesterday);
            log.info("✅ [Scheduler] Reconciliation completed for: {}", yesterday);
        } catch (Exception e) {
            log.error("❌ [Scheduler] Reconciliation failed for {}: {}", yesterday, e.getMessage(), e);
        }
    }

    /**
     * Tính toán lại thống kê cho 1 ngày cụ thể từ raw events
     */
    public void reconcileDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<AnalyticsEvent> events = eventRepo.findByEventTimeBetween(startOfDay, endOfDay);

        if (events.isEmpty()) {
            log.info("📊 No events found for date: {}", date);
            return;
        }

        // ============ PLATFORM-WIDE STATS ============
        long totalCreated = events.stream().filter(e -> "ORDER_CREATED".equals(e.getEventType())).count();
        long totalDelivered = events.stream().filter(e -> "ORDER_DELIVERED".equals(e.getEventType())).count();
        long totalCancelled = events.stream().filter(e -> "ORDER_CANCELLED".equals(e.getEventType())).count();
        BigDecimal totalRevenue = events.stream()
                .filter(e -> "ORDER_DELIVERED".equals(e.getEventType()) && e.getAmount() != null)
                .map(AnalyticsEvent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DailyOrderStats platformStats = orderStatsRepo.findByStatDateAndRestaurantIdIsNull(date)
                .orElse(DailyOrderStats.builder().statDate(date).restaurantId(null).build());
        platformStats.setTotalOrders(totalCreated);
        platformStats.setDeliveredOrders(totalDelivered);
        platformStats.setCancelledOrders(totalCancelled);
        platformStats.setPendingOrders(Math.max(0, totalCreated - totalDelivered - totalCancelled));
        platformStats.setTotalRevenue(totalRevenue);
        platformStats.setTotalShippingFee(BigDecimal.ZERO);
        platformStats.setTotalDiscount(BigDecimal.ZERO);
        platformStats.setAvgOrderValue(totalDelivered > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalDelivered), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        platformStats.setNewCustomers(0);
        orderStatsRepo.save(platformStats);

        // ============ PER-RESTAURANT STATS ============
        Map<Long, List<AnalyticsEvent>> byRestaurant = events.stream()
                .filter(e -> e.getRestaurantId() != null)
                .collect(Collectors.groupingBy(AnalyticsEvent::getRestaurantId));

        for (Map.Entry<Long, List<AnalyticsEvent>> entry : byRestaurant.entrySet()) {
            Long restaurantId = entry.getKey();
            List<AnalyticsEvent> rEvents = entry.getValue();

            long rCreated = rEvents.stream().filter(e -> "ORDER_CREATED".equals(e.getEventType())).count();
            long rDelivered = rEvents.stream().filter(e -> "ORDER_DELIVERED".equals(e.getEventType())).count();
            long rCancelled = rEvents.stream().filter(e -> "ORDER_CANCELLED".equals(e.getEventType())).count();
            BigDecimal rRevenue = rEvents.stream()
                    .filter(e -> "ORDER_DELIVERED".equals(e.getEventType()) && e.getAmount() != null)
                    .map(AnalyticsEvent::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            DailyOrderStats rStats = orderStatsRepo.findByStatDateAndRestaurantId(date, restaurantId)
                    .orElse(DailyOrderStats.builder().statDate(date).restaurantId(restaurantId).build());
            rStats.setTotalOrders(rCreated);
            rStats.setDeliveredOrders(rDelivered);
            rStats.setCancelledOrders(rCancelled);
            rStats.setPendingOrders(Math.max(0, rCreated - rDelivered - rCancelled));
            rStats.setTotalRevenue(rRevenue);
            rStats.setTotalShippingFee(BigDecimal.ZERO);
            rStats.setTotalDiscount(BigDecimal.ZERO);
            rStats.setAvgOrderValue(rDelivered > 0
                    ? rRevenue.divide(BigDecimal.valueOf(rDelivered), 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            rStats.setNewCustomers(0);
            orderStatsRepo.save(rStats);
        }

        log.info("📊 Reconciled {} events for date {} → Platform: {} orders, {} delivered, {} revenue | {} restaurants processed",
                events.size(), date, totalCreated, totalDelivered, totalRevenue, byRestaurant.size());
    }
}
