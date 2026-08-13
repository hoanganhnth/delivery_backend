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
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AnalyticsEventRepository eventRepo;
    private final DailyOrderStatsRepository orderStatsRepo;
    private final DailyRevenueStatsRepository revenueStatsRepo;

    @Value("${spring.datasource.url:}")
    private String dataSourceUrl;

    // ==================== ORDER EVENTS ====================

    /**
     * Xử lý event: Đơn hàng mới được tạo
     */
    @Transactional
    public void processOrderCreated(Long orderId, Long userId, Long restaurantId,
                                     String restaurantName, BigDecimal totalPrice,
                                     String paymentMethod, String rawPayload) {
        String deduplicationKey = resolveDeduplicationKey("ORDER_CREATED", orderId, rawPayload);
        if (!claimEvent(deduplicationKey, "ORDER_CREATED", orderId, userId,
                restaurantId, restaurantName, totalPrice, "PENDING", paymentMethod, rawPayload)) {
            return;
        }
        LocalDate today = LocalDate.now();

        if (isPostgres()) {
            orderStatsRepo.incrementCreatedPostgres(today, null);
            if (restaurantId != null) orderStatsRepo.incrementCreatedPostgres(today, restaurantId);
            return;
        }

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
        String deduplicationKey = resolveDeduplicationKey("ORDER_DELIVERED", orderId, rawPayload);
        if (!claimEvent(deduplicationKey, "ORDER_DELIVERED", orderId, null,
                restaurantId, restaurantName, totalPrice, "DELIVERED", null, rawPayload)) {
            return;
        }
        LocalDate today = LocalDate.now();
        BigDecimal safeTotal = totalPrice != null ? totalPrice : BigDecimal.ZERO;
        if (isPostgres()) {
            orderStatsRepo.incrementDeliveredPostgres(today, null, safeTotal);
            if (restaurantId != null) {
                orderStatsRepo.incrementDeliveredPostgres(today, restaurantId, safeTotal);
            }
            return;
        }

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
        String deduplicationKey = resolveDeduplicationKey("ORDER_CANCELLED", orderId, rawPayload);
        if (!claimEvent(deduplicationKey, "ORDER_CANCELLED", orderId, null,
                restaurantId, null, null, "CANCELLED", null, rawPayload)) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (isPostgres()) {
            orderStatsRepo.incrementCancelledPostgres(today, null);
            if (restaurantId != null) orderStatsRepo.incrementCancelledPostgres(today, restaurantId);
            return;
        }

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
        String deduplicationKey = resolveDeduplicationKey("PAYMENT_COMPLETED", orderId, rawPayload);
        BigDecimal safeAmount = amount != null ? BigDecimal.valueOf(amount) : BigDecimal.ZERO;
        if (!claimEvent(deduplicationKey, "PAYMENT_COMPLETED", orderId, userId,
                null, null, safeAmount, null, paymentMethod, rawPayload)) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (isPostgres()) {
            revenueStatsRepo.incrementPaymentCompletedPostgres(today, safeAmount);
            return;
        }

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
        String deduplicationKey = resolveDeduplicationKey("PAYMENT_FAILED", orderId, rawPayload);
        if (!claimEvent(deduplicationKey, "PAYMENT_FAILED", orderId, null,
                null, null, null, null, null, rawPayload)) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (isPostgres()) {
            revenueStatsRepo.incrementPaymentFailedPostgres(today);
            return;
        }

        DailyRevenueStats platRevStats = getOrCreateRevenueStats(today, null);
        platRevStats.setFailedPayments(platRevStats.getFailedPayments() + 1);
        revenueStatsRepo.save(platRevStats);

        log.info("📊 Processed PAYMENT_FAILED: orderId={}", orderId);
    }

    // ==================== HELPERS ====================

    private boolean claimEvent(String key, String type, Long orderId, Long userId,
                               Long restaurantId, String restaurantName, BigDecimal amount,
                               String orderStatus, String paymentMethod, String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new IllegalArgumentException("Analytics raw payload is required");
        }
        String fingerprint = fingerprint(rawPayload);
        AnalyticsEvent existing = eventRepo.findByDeduplicationKey(key).orElse(null);
        if (existing == null) {
            if (isPostgres()) {
                int inserted = eventRepo.insertIfAbsentPostgres(key, type, orderId, userId,
                        restaurantId, restaurantName, amount, orderStatus, paymentMethod,
                        rawPayload, fingerprint);
                if (inserted == 1) return true;
                existing = eventRepo.findByDeduplicationKey(key).orElseThrow(() ->
                        new IllegalStateException("analytics receipt conflict resolved without a committed row"));
            } else {
                eventRepo.saveAndFlush(AnalyticsEvent.builder()
                        .deduplicationKey(key).eventType(type).eventTime(LocalDateTime.now())
                        .orderId(orderId).userId(userId).restaurantId(restaurantId)
                        .restaurantName(restaurantName).amount(amount).orderStatus(orderStatus)
                        .paymentMethod(paymentMethod).rawPayload(rawPayload)
                        .payloadFingerprint(fingerprint).build());
                return true;
            }
        }
        requireExactReplay(existing, type, orderId, userId, restaurantId,
                restaurantName, amount, orderStatus, paymentMethod, rawPayload, fingerprint);
        log.info("Skipping exact analytics replay {}", key);
        return false;
    }

    private void requireExactReplay(AnalyticsEvent existing, String type, Long orderId, Long userId,
                                    Long restaurantId, String restaurantName, BigDecimal amount,
                                    String orderStatus, String paymentMethod, String rawPayload,
                                    String fingerprint) {
        boolean payloadMatches = existing.getPayloadFingerprint() != null
                ? existing.getPayloadFingerprint().equals(fingerprint)
                : Objects.equals(existing.getRawPayload(), rawPayload);
        if (!existing.getEventType().equals(type)
                || !Objects.equals(existing.getOrderId(), orderId)
                || !Objects.equals(existing.getUserId(), userId)
                || !Objects.equals(existing.getRestaurantId(), restaurantId)
                || !Objects.equals(existing.getRestaurantName(), restaurantName)
                || !sameAmount(existing.getAmount(), amount)
                || !Objects.equals(existing.getOrderStatus(), orderStatus)
                || !Objects.equals(existing.getPaymentMethod(), paymentMethod)
                || !payloadMatches) {
            throw new IllegalArgumentException(
                    "analytics deduplication key replay has contradictory identity or payload");
        }
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private boolean isPostgres() {
        return dataSourceUrl != null && dataSourceUrl.startsWith("jdbc:postgresql:");
    }

    private String fingerprint(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static String resolveDeduplicationKey(String eventType, Long orderId, String rawPayload) {
        if (rawPayload != null && !rawPayload.isBlank()) {
            try {
                JsonNode eventId = OBJECT_MAPPER.readTree(rawPayload).path("eventId");
                if (eventId.isTextual() && !eventId.asText().isBlank()) {
                    return eventType + ":event:" + eventId.asText();
                }
            } catch (Exception ignored) {
                // Listener owns payload validation; legacy producers may not carry eventId.
            }
        }
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Analytics event requires a positive orderId");
        }
        return eventType + ":order:" + orderId;
    }

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
