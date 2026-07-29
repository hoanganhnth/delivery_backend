package com.delivery.analytics_service.scheduler;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import com.delivery.analytics_service.entity.DailyOrderStats;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

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
@ConditionalOnProperty(name = "app.analytics.processing-enabled", havingValue = "true")
public class StatsReconciliationJob {

    private static final int RECONCILIATION_PAGE_SIZE = 500;

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

        EventCounts platform = new EventCounts();
        Map<Long, EventCounts> byRestaurant = new HashMap<>();
        long processed = 0;
        Pageable pageable = PageRequest.of(
                0, RECONCILIATION_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"));

        while (true) {
            Page<AnalyticsEvent> page = eventRepo.findByEventTimeBetween(
                    startOfDay, endOfDay, pageable);
            for (AnalyticsEvent event : page.getContent()) {
                platform.accept(event);
                if (event.getRestaurantId() != null) {
                    byRestaurant.computeIfAbsent(event.getRestaurantId(), ignored -> new EventCounts())
                            .accept(event);
                }
                processed++;
            }
            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        if (processed == 0) {
            log.info("📊 No events found for date: {}", date);
            return;
        }

        // ============ PLATFORM-WIDE STATS ============
        DailyOrderStats platformStats = orderStatsRepo.findByStatDateAndRestaurantIdIsNull(date)
                .orElse(DailyOrderStats.builder().statDate(date).restaurantId(null).build());
        applyCounts(platformStats, platform);
        orderStatsRepo.save(platformStats);

        // ============ PER-RESTAURANT STATS ============
        for (Map.Entry<Long, EventCounts> entry : byRestaurant.entrySet()) {
            Long restaurantId = entry.getKey();
            DailyOrderStats rStats = orderStatsRepo.findByStatDateAndRestaurantId(date, restaurantId)
                    .orElse(DailyOrderStats.builder().statDate(date).restaurantId(restaurantId).build());
            applyCounts(rStats, entry.getValue());
            orderStatsRepo.save(rStats);
        }

        log.info("📊 Reconciled {} events for date {} → Platform: {} orders, {} delivered, {} revenue | {} restaurants processed",
                processed, date, platform.created, platform.delivered,
                platform.revenue, byRestaurant.size());
    }

    private void applyCounts(DailyOrderStats stats, EventCounts counts) {
        stats.setTotalOrders(counts.created);
        stats.setDeliveredOrders(counts.delivered);
        stats.setCancelledOrders(counts.cancelled);
        stats.setPendingOrders(Math.max(0, counts.created - counts.delivered - counts.cancelled));
        stats.setTotalRevenue(counts.revenue);
        stats.setTotalShippingFee(BigDecimal.ZERO);
        stats.setTotalDiscount(BigDecimal.ZERO);
        stats.setAvgOrderValue(counts.delivered > 0
                ? counts.revenue.divide(BigDecimal.valueOf(counts.delivered), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        stats.setNewCustomers(0);
    }

    private static final class EventCounts {
        private long created;
        private long delivered;
        private long cancelled;
        private BigDecimal revenue = BigDecimal.ZERO;

        private void accept(AnalyticsEvent event) {
            switch (event.getEventType()) {
                case "ORDER_CREATED" -> created++;
                case "ORDER_DELIVERED" -> {
                    delivered++;
                    if (event.getAmount() != null) {
                        revenue = revenue.add(event.getAmount());
                    }
                }
                case "ORDER_CANCELLED" -> cancelled++;
                default -> {
                    // Other raw event types do not affect order reconciliation totals.
                }
            }
        }
    }
}
