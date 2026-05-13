package com.delivery.analytics_service.service;

import com.delivery.analytics_service.entity.AnalyticsEvent;
import com.delivery.analytics_service.entity.DailyOrderStats;
import com.delivery.analytics_service.entity.DailyRevenueStats;
import com.delivery.analytics_service.repository.AnalyticsEventRepository;
import com.delivery.analytics_service.repository.DailyOrderStatsRepository;
import com.delivery.analytics_service.repository.DailyRevenueStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Service xử lý events từ Kafka và cập nhật bảng thống kê
 *
 * Cơ chế:
 * 1. Mỗi event từ Kafka → lưu vào bảng AnalyticsEvent (raw log)
 * 2. Đồng thời cập nhật (upsert) bảng DailyOrderStats / DailyRevenueStats
 * 3. Scheduled Job chạy hàng đêm sẽ re-compute từ raw events để đảm bảo accuracy
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventProcessingService {

    private final AnalyticsEventRepository eventRepo;
    private final DailyOrderStatsRepository orderStatsRepo;
    private final DailyRevenueStatsRepository revenueStatsRepo;

    // ==================== ORDER EVENTS ====================

    /**
     * Xử lý event: Đơn hàng mới được tạo
     */
    @Transactional
    public void processOrderCreated(Long orderId, Long userId, Long restaurantId,
                                     String restaurantName, BigDecimal totalPrice,
                                     String paymentMethod, String rawPayload) {
        LocalDate today = LocalDate.now();

        // 1. Lưu raw event
        eventRepo.save(AnalyticsEvent.builder()
                .eventType("ORDER_CREATED")
                .eventTime(LocalDateTime.now())
                .orderId(orderId)
                .userId(userId)
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .amount(totalPrice)
                .orderStatus("PENDING")
                .paymentMethod(paymentMethod)
                .rawPayload(rawPayload)
                .build());

        // 2. Cập nhật platform stats (restaurantId = null)
        DailyOrderStats platformStats = getOrCreateOrderStats(today, null);
        platformStats.setTotalOrders(platformStats.getTotalOrders() + 1);
        platformStats.setPendingOrders(platformStats.getPendingOrders() + 1);
        orderStatsRepo.save(platformStats);

        // 3. Cập nhật restaurant stats
        if (restaurantId != null) {
            DailyOrderStats restaurantStats = getOrCreateOrderStats(today, restaurantId);
            restaurantStats.setTotalOrders(restaurantStats.getTotalOrders() + 1);
            restaurantStats.setPendingOrders(restaurantStats.getPendingOrders() + 1);
            orderStatsRepo.save(restaurantStats);
        }

        log.info("📊 Processed ORDER_CREATED: orderId={}, restaurantId={}", orderId, restaurantId);
    }

    /**
     * Xử lý event: Đơn hàng được giao thành công (DELIVERED)
     */
    @Transactional
    public void processOrderDelivered(Long orderId, Long restaurantId, String restaurantName,
                                       BigDecimal totalPrice, String rawPayload) {
        LocalDate today = LocalDate.now();

        eventRepo.save(AnalyticsEvent.builder()
                .eventType("ORDER_DELIVERED")
                .eventTime(LocalDateTime.now())
                .orderId(orderId)
                .restaurantId(restaurantId)
                .restaurantName(restaurantName)
                .amount(totalPrice)
                .orderStatus("DELIVERED")
                .rawPayload(rawPayload)
                .build());

        // Platform stats
        DailyOrderStats platformStats = getOrCreateOrderStats(today, null);
        platformStats.setDeliveredOrders(platformStats.getDeliveredOrders() + 1);
        if (platformStats.getPendingOrders() > 0) {
            platformStats.setPendingOrders(platformStats.getPendingOrders() - 1);
        }
        BigDecimal newRevenue = platformStats.getTotalRevenue().add(totalPrice != null ? totalPrice : BigDecimal.ZERO);
        platformStats.setTotalRevenue(newRevenue);
        recalcAvg(platformStats);
        orderStatsRepo.save(platformStats);

        // Restaurant stats
        if (restaurantId != null) {
            DailyOrderStats rStats = getOrCreateOrderStats(today, restaurantId);
            rStats.setDeliveredOrders(rStats.getDeliveredOrders() + 1);
            if (rStats.getPendingOrders() > 0) {
                rStats.setPendingOrders(rStats.getPendingOrders() - 1);
            }
            BigDecimal rRevenue = rStats.getTotalRevenue().add(totalPrice != null ? totalPrice : BigDecimal.ZERO);
            rStats.setTotalRevenue(rRevenue);
            recalcAvg(rStats);
            orderStatsRepo.save(rStats);
        }

        log.info("📊 Processed ORDER_DELIVERED: orderId={}, revenue={}", orderId, totalPrice);
    }

    /**
     * Xử lý event: Đơn hàng bị hủy (CANCELLED)
     */
    @Transactional
    public void processOrderCancelled(Long orderId, Long restaurantId, String rawPayload) {
        LocalDate today = LocalDate.now();

        eventRepo.save(AnalyticsEvent.builder()
                .eventType("ORDER_CANCELLED")
                .eventTime(LocalDateTime.now())
                .orderId(orderId)
                .restaurantId(restaurantId)
                .orderStatus("CANCELLED")
                .rawPayload(rawPayload)
                .build());

        // Platform
        DailyOrderStats platformStats = getOrCreateOrderStats(today, null);
        platformStats.setCancelledOrders(platformStats.getCancelledOrders() + 1);
        if (platformStats.getPendingOrders() > 0) {
            platformStats.setPendingOrders(platformStats.getPendingOrders() - 1);
        }
        orderStatsRepo.save(platformStats);

        // Restaurant
        if (restaurantId != null) {
            DailyOrderStats rStats = getOrCreateOrderStats(today, restaurantId);
            rStats.setCancelledOrders(rStats.getCancelledOrders() + 1);
            if (rStats.getPendingOrders() > 0) {
                rStats.setPendingOrders(rStats.getPendingOrders() - 1);
            }
            orderStatsRepo.save(rStats);
        }

        log.info("📊 Processed ORDER_CANCELLED: orderId={}", orderId);
    }

    // ==================== PAYMENT EVENTS ====================

    /**
     * Xử lý event: Thanh toán thành công
     */
    @Transactional
    public void processPaymentCompleted(Long orderId, Long userId, Double amount,
                                         String paymentMethod, String rawPayload) {
        LocalDate today = LocalDate.now();

        eventRepo.save(AnalyticsEvent.builder()
                .eventType("PAYMENT_COMPLETED")
                .eventTime(LocalDateTime.now())
                .orderId(orderId)
                .userId(userId)
                .amount(amount != null ? BigDecimal.valueOf(amount) : BigDecimal.ZERO)
                .paymentMethod(paymentMethod)
                .rawPayload(rawPayload)
                .build());

        // Platform revenue stats
        DailyRevenueStats platRevStats = getOrCreateRevenueStats(today, null);
        platRevStats.setSuccessfulPayments(platRevStats.getSuccessfulPayments() + 1);
        BigDecimal amt = amount != null ? BigDecimal.valueOf(amount) : BigDecimal.ZERO;
        platRevStats.setTotalPaymentAmount(platRevStats.getTotalPaymentAmount().add(amt));
        revenueStatsRepo.save(platRevStats);

        log.info("📊 Processed PAYMENT_COMPLETED: orderId={}, amount={}", orderId, amount);
    }

    /**
     * Xử lý event: Thanh toán thất bại
     */
    @Transactional
    public void processPaymentFailed(Long orderId, String rawPayload) {
        LocalDate today = LocalDate.now();

        eventRepo.save(AnalyticsEvent.builder()
                .eventType("PAYMENT_FAILED")
                .eventTime(LocalDateTime.now())
                .orderId(orderId)
                .rawPayload(rawPayload)
                .build());

        DailyRevenueStats platRevStats = getOrCreateRevenueStats(today, null);
        platRevStats.setFailedPayments(platRevStats.getFailedPayments() + 1);
        revenueStatsRepo.save(platRevStats);

        log.info("📊 Processed PAYMENT_FAILED: orderId={}", orderId);
    }

    // ==================== HELPERS ====================

    private DailyOrderStats getOrCreateOrderStats(LocalDate date, Long restaurantId) {
        if (restaurantId == null) {
            return orderStatsRepo.findByStatDateAndRestaurantIdIsNull(date)
                    .orElseGet(() -> DailyOrderStats.builder()
                            .statDate(date)
                            .restaurantId(null)
                            .totalOrders(0)
                            .deliveredOrders(0)
                            .cancelledOrders(0)
                            .pendingOrders(0)
                            .totalRevenue(BigDecimal.ZERO)
                            .totalShippingFee(BigDecimal.ZERO)
                            .totalDiscount(BigDecimal.ZERO)
                            .avgOrderValue(BigDecimal.ZERO)
                            .newCustomers(0)
                            .build());
        }
        return orderStatsRepo.findByStatDateAndRestaurantId(date, restaurantId)
                .orElseGet(() -> DailyOrderStats.builder()
                        .statDate(date)
                        .restaurantId(restaurantId)
                        .totalOrders(0)
                        .deliveredOrders(0)
                        .cancelledOrders(0)
                        .pendingOrders(0)
                        .totalRevenue(BigDecimal.ZERO)
                        .totalShippingFee(BigDecimal.ZERO)
                        .totalDiscount(BigDecimal.ZERO)
                        .avgOrderValue(BigDecimal.ZERO)
                        .newCustomers(0)
                        .build());
    }

    private DailyRevenueStats getOrCreateRevenueStats(LocalDate date, Long restaurantId) {
        if (restaurantId == null) {
            return revenueStatsRepo.findByStatDateAndRestaurantIdIsNull(date)
                    .orElseGet(() -> DailyRevenueStats.builder()
                            .statDate(date)
                            .restaurantId(null)
                            .totalPaymentAmount(BigDecimal.ZERO)
                            .successfulPayments(0)
                            .failedPayments(0)
                            .totalWithdrawals(BigDecimal.ZERO)
                            .platformFee(BigDecimal.ZERO)
                            .build());
        }
        return revenueStatsRepo.findByStatDateAndRestaurantId(date, restaurantId)
                .orElseGet(() -> DailyRevenueStats.builder()
                        .statDate(date)
                        .restaurantId(restaurantId)
                        .totalPaymentAmount(BigDecimal.ZERO)
                        .successfulPayments(0)
                        .failedPayments(0)
                        .totalWithdrawals(BigDecimal.ZERO)
                        .platformFee(BigDecimal.ZERO)
                        .build());
    }

    private void recalcAvg(DailyOrderStats stats) {
        if (stats.getDeliveredOrders() > 0) {
            stats.setAvgOrderValue(
                stats.getTotalRevenue().divide(BigDecimal.valueOf(stats.getDeliveredOrders()), 0, RoundingMode.HALF_UP)
            );
        }
    }
}
