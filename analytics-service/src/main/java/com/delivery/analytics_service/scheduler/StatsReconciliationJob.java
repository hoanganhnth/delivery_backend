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
 * - Scheduled Job phục hồi order projections từ raw events đã được lưu.
 * - Không thể phục hồi events chưa được ingest hoặc payment/item projections.
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
            throw e;
        }
    }

    /**
     * Tính toán lại thống kê cho 1 ngày cụ thể từ raw events
     */
    @Transactional
    public void reconcileDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        OrderReconciliationAccumulator platform = new OrderReconciliationAccumulator();
        Map<Long, OrderReconciliationAccumulator> byRestaurant = new HashMap<>();
        long processed = 0;
        Pageable pageable = PageRequest.of(
                0, RECONCILIATION_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"));

        while (true) {
            Page<AnalyticsEvent> page = eventRepo.findByEventTimeBetween(
                    startOfDay, endOfDay, pageable);
            for (AnalyticsEvent event : page.getContent()) {
                platform.accept(event.getEventType(), event.getAmount());
                if (event.getRestaurantId() != null) {
                    byRestaurant.computeIfAbsent(event.getRestaurantId(),
                                    ignored -> new OrderReconciliationAccumulator())
                            .accept(event.getEventType(), event.getAmount());
                }
                processed++;
            }
            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        resetUnobservedScopes(date, byRestaurant, processed > 0);

        if (processed == 0) {
            log.info("📊 No events found for date: {}", date);
            return;
        }

        // ============ PLATFORM-WIDE STATS ============
        DailyOrderStats platformStats = orderStatsRepo.findByStatDateAndRestaurantIdIsNull(date)
                .orElse(DailyOrderStats.builder().statDate(date).restaurantId(null).build());
        var platformCounts = platform.snapshot();
        applyCounts(platformStats, platformCounts);
        orderStatsRepo.save(platformStats);

        // ============ PER-RESTAURANT STATS ============
        for (Map.Entry<Long, OrderReconciliationAccumulator> entry : byRestaurant.entrySet()) {
            Long restaurantId = entry.getKey();
            DailyOrderStats rStats = orderStatsRepo.findByStatDateAndRestaurantId(date, restaurantId)
                    .orElse(DailyOrderStats.builder().statDate(date).restaurantId(restaurantId).build());
            applyCounts(rStats, entry.getValue().snapshot());
            orderStatsRepo.save(rStats);
        }

        log.info("📊 Reconciled {} events for date {} → Platform: {} orders, {} delivered, {} revenue | {} restaurants processed",
                processed, date, platformCounts.created(), platformCounts.delivered(),
                platformCounts.revenue(), byRestaurant.size());
    }

    private void resetUnobservedScopes(LocalDate date,
                                       Map<Long, OrderReconciliationAccumulator> observedRestaurants,
                                       boolean hasPlatformEvents) {
        var zero = new OrderReconciliationAccumulator().snapshot();
        Pageable pageable = PageRequest.of(0, RECONCILIATION_PAGE_SIZE,
                Sort.by(Sort.Direction.ASC, "id"));
        while (true) {
            Page<DailyOrderStats> page = orderStatsRepo.findByStatDate(date, pageable);
            for (DailyOrderStats row : page.getContent()) {
                boolean observed = row.getRestaurantId() == null
                        ? hasPlatformEvents : observedRestaurants.containsKey(row.getRestaurantId());
                if (!observed) {
                    applyCounts(row, zero);
                    orderStatsRepo.save(row);
                }
            }
            if (!page.hasNext()) return;
            pageable = page.nextPageable();
        }
    }

    private void applyCounts(DailyOrderStats stats, OrderReconciliationAccumulator.Snapshot counts) {
        stats.setTotalOrders(counts.created());
        stats.setDeliveredOrders(counts.delivered());
        stats.setCancelledOrders(counts.cancelled());
        stats.setPendingOrders(counts.pending());
        stats.setTotalRevenue(counts.revenue());
        stats.setTotalShippingFee(BigDecimal.ZERO);
        stats.setTotalDiscount(BigDecimal.ZERO);
        stats.setAvgOrderValue(counts.averageOrderValue());
        stats.setNewCustomers(0);
    }

}
