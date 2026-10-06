package com.delivery.analytics_service.scheduler;

import com.delivery.analytics.domain.OrderReconciliationAccumulator;
import com.delivery.analytics.application.ReconciliationService;
import com.delivery.analytics.applicationapi.ReconciliationPort;
import com.delivery.analytics_service.entity.DailyOrderStats;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

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
        var result = new ReconciliationService(new ReconciliationPort() {
            public ReconciliationPort.Page<Receipt> receipts(LocalDate day,int page,int size) {
                var rows=eventRepo.findByEventTimeBetween(day.atStartOfDay(),day.atTime(LocalTime.MAX),pageRequest(page,size));
                return new ReconciliationPort.Page<>(rows.getContent().stream().map(e ->
                        new Receipt(e.getEventType(),e.getRestaurantId(),e.getAmount())).toList(),rows.hasNext());
            }
            public ReconciliationPort.Page<Scope> scopes(LocalDate day,int page,int size) {
                var rows=orderStatsRepo.findByStatDate(day,pageRequest(page,size));
                var scopes=rows.getContent().stream().map(row -> (Scope)new Scope() {
                    public Long restaurantId() { return row.getRestaurantId(); }
                    public void overwrite(OrderReconciliationAccumulator.Snapshot snapshot) {
                        applyCounts(row,snapshot);orderStatsRepo.save(row);
                    }
                }).toList();
                return new ReconciliationPort.Page<>(scopes,rows.hasNext());
            }
            public void overwrite(LocalDate day,Long restaurantId,OrderReconciliationAccumulator.Snapshot snapshot) {
                var row=restaurantId==null?orderStatsRepo.findByStatDateAndRestaurantIdIsNull(day)
                        :orderStatsRepo.findByStatDateAndRestaurantId(day,restaurantId);
                var stats=row.orElse(DailyOrderStats.builder().statDate(day).restaurantId(restaurantId).build());
                applyCounts(stats,snapshot);orderStatsRepo.save(stats);
            }
        }).reconcile(date);
        if (result.processed() == 0) {
            log.info("📊 No events found for date: {}", date);
            return;
        }
        var platformCounts = result.platform();
        log.info("📊 Reconciled {} events for date {} → Platform: {} orders, {} delivered, {} revenue | {} restaurants processed",
                result.processed(), date, platformCounts.created(), platformCounts.delivered(),
                platformCounts.revenue(), result.restaurants());
    }
    private Pageable pageRequest(int page,int size) {
        return PageRequest.of(page,size,Sort.by(Sort.Direction.ASC,"id"));
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
