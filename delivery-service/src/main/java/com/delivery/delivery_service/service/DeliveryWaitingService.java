package com.delivery.delivery_service.service;

import com.delivery.delivery_service.dto.event.ShipperFoundEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ✅ Service để handle Redis operations cho "chờ shipper nhận đơn"
 * Graceful degradation: nếu Redis không available, các tính năng Redis sẽ bị
 * skip
 * nhưng các API khác (lấy danh sách delivery...) vẫn hoạt động bình thường.
 */
@Service
@Slf4j
public class DeliveryWaitingService {

    @Nullable
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String WAITING_KEY_PREFIX = "delivery:waiting:";
    private static final int DEFAULT_WAITING_TIMEOUT_SECONDS = 300; // 5 minutes

    public DeliveryWaitingService(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        if (redisTemplate == null) {
            log.warn("⚠️ Redis is not available – DeliveryWaitingService running in degraded mode. " +
                    "Shipper auto-retry via TTL will be disabled.");
        }
    }

    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }

    /**
     * ✅ Cache trạng thái "chờ shipper nhận" với TTL tự động trigger retry via Redis
     * Keyspace Notifications
     */
    public void cacheWaitingForShipperAcceptance(ShipperFoundEvent event) {
        if (!isRedisAvailable()) {
            log.warn("⚠️ Redis unavailable – skipping cache for delivery: {}", event.getDeliveryId());
            return;
        }
        try {
            String key = WAITING_KEY_PREFIX + event.getDeliveryId();

            WaitingState waitingState = new WaitingState(
                    event.getDeliveryId(),
                    event.getOrderId(),
                    LocalDateTime.now(),
                    event.getWaitingTimeoutSeconds() != null ? event.getWaitingTimeoutSeconds()
                            : DEFAULT_WAITING_TIMEOUT_SECONDS,
                    event.getMatchingSessionId(),
                    event.getAvailableShippers() != null ? event.getAvailableShippers().size() : 0);

            Duration ttl = Duration.ofSeconds(waitingState.getTimeoutSeconds());
            redisTemplate.opsForValue().set(key, waitingState, ttl);

            log.info("✅ Cached waiting state for delivery: {} with TTL: {} seconds",
                    event.getDeliveryId(), waitingState.getTimeoutSeconds());

        } catch (Exception e) {
            log.error("💥 Error caching waiting state for delivery: {}: {}",
                    event.getDeliveryId(), e.getMessage(), e);
        }
    }

    /**
     * ✅ Remove waiting state khi shipper accept
     */
    public void removeWaitingState(Long deliveryId) {
        if (!isRedisAvailable()) {
            log.warn("⚠️ Redis unavailable – skipping remove waiting state for delivery: {}", deliveryId);
            return;
        }
        try {
            String key = WAITING_KEY_PREFIX + deliveryId;
            Boolean deleted = redisTemplate.delete(key);

            if (Boolean.TRUE.equals(deleted)) {
                log.info("✅ Removed waiting state for delivery: {} - shipper accepted", deliveryId);
            } else {
                log.warn("⚠️ Waiting state not found for delivery: {} - may have expired", deliveryId);
            }

        } catch (Exception e) {
            log.error("💥 Error removing waiting state for delivery: {}: {}",
                    deliveryId, e.getMessage(), e);
        }
    }

    /**
     * ✅ Get waiting state from Redis
     */
    public WaitingState getWaitingState(Long deliveryId) {
        if (!isRedisAvailable()) {
            return null;
        }
        try {
            String key = WAITING_KEY_PREFIX + deliveryId;
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached instanceof WaitingState) {
                return (WaitingState) cached;
            } else if (cached instanceof Map) {
                return mapToWaitingState((Map<?, ?>) cached);
            }

            return null;

        } catch (Exception e) {
            log.error("💥 Error getting waiting state for delivery: {}: {}",
                    deliveryId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * ✅ Check if delivery is in waiting state
     */
    public boolean isWaitingForShipper(Long deliveryId) {
        if (!isRedisAvailable()) {
            return false;
        }
        String key = WAITING_KEY_PREFIX + deliveryId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * ✅ Handle TTL expiration - retry find shipper
     */
    public void handleWaitingTimeout(Long deliveryId) {
        try {
            log.warn("⏰ Waiting timeout for delivery: {} - retrying shipper search", deliveryId);

            WaitingState waitingState = getWaitingState(deliveryId);
            if (waitingState != null) {
                // ✅ Republish FindShipperEvent để tìm shipper lại
                republishFindShipperEvent(waitingState);

                // Remove expired waiting state
                removeWaitingState(deliveryId);
            }

        } catch (Exception e) {
            log.error("💥 Error handling waiting timeout for delivery: {}: {}",
                    deliveryId, e.getMessage(), e);
        }
    }

    /**
     * ✅ Republish FindShipperEvent để retry tìm shipper
     */
    private void republishFindShipperEvent(WaitingState waitingState) {
        try {
            // Create new FindShipperEvent from waiting state
            // Note: Cần thêm thông tin location vào WaitingState nếu cần
            log.info("🔄 Republishing FindShipperEvent for delivery: {} after timeout",
                    waitingState.getDeliveryId());

            // TODO: Call deliveryEventPublisher.publishFindShipperEvent()
            // Cần expand WaitingState để có thông tin location

        } catch (Exception e) {
            log.error("💥 Error republishing FindShipperEvent for delivery: {}: {}",
                    waitingState.getDeliveryId(), e.getMessage(), e);
        }
    }

    /**
     * ✅ Convert Map to WaitingState (để handle Redis deserialization)
     */
    private WaitingState mapToWaitingState(Map<?, ?> map) {
        // Implementation để convert Map từ Redis về WaitingState object
        // TODO: Implement proper mapping
        return new WaitingState();
    }

    /**
     * ✅ Inner class để represent waiting state
     */
    public static class WaitingState {
        private Long deliveryId;
        private Long orderId;
        private LocalDateTime createdAt;
        private Integer timeoutSeconds;
        private String matchingSessionId;
        private Integer shippersCount;

        public WaitingState() {
        }

        // ✅ Constructor cho cache operation
        public WaitingState(Long deliveryId, Long orderId, LocalDateTime createdAt,
                Integer timeoutSeconds, String matchingSessionId, Integer shippersCount) {
            this.deliveryId = deliveryId;
            this.orderId = orderId;
            this.createdAt = createdAt;
            this.timeoutSeconds = timeoutSeconds;
            this.matchingSessionId = matchingSessionId;
            this.shippersCount = shippersCount;
        }

        // ✅ Legacy constructor for backward compatibility
        @Deprecated
        public WaitingState(Long deliveryId, Long orderId, Object availableShippers,
                LocalDateTime createdAt, Integer timeoutSeconds, String matchingSessionId) {
            this.deliveryId = deliveryId;
            this.orderId = orderId;
            this.createdAt = createdAt;
            this.timeoutSeconds = timeoutSeconds;
            this.matchingSessionId = matchingSessionId;
            this.shippersCount = availableShippers instanceof List ? ((List<?>) availableShippers).size() : 0;
        }

        // Getters and setters
        public Long getDeliveryId() {
            return deliveryId;
        }

        public void setDeliveryId(Long deliveryId) {
            this.deliveryId = deliveryId;
        }

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Integer getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getMatchingSessionId() {
            return matchingSessionId;
        }

        public void setMatchingSessionId(String matchingSessionId) {
            this.matchingSessionId = matchingSessionId;
        }

        public Integer getShippersCount() {
            return shippersCount;
        }

        public void setShippersCount(Integer shippersCount) {
            this.shippersCount = shippersCount;
        }
    }
}
