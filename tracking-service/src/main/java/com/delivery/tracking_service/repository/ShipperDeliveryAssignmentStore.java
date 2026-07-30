package com.delivery.tracking_service.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/** Shared Redis routing projection derived from durable shipper status events. */
@Repository
public class ShipperDeliveryAssignmentStore {

    private static final String PREFIX = "tracking:shipper:active-delivery:";
    private static final Duration TTL = Duration.ofHours(24);
    private final StringRedisTemplate redis;

    public ShipperDeliveryAssignmentStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public synchronized void busy(long shipperId, long deliveryId, long timestamp, String eventId) {
        Assignment current = read(shipperId).orElse(null);
        if (current != null && timestamp < current.timestamp()) return;
        redis.opsForValue().set(key(shipperId), encode(deliveryId, timestamp, eventId), TTL);
    }

    public synchronized void available(long shipperId, long deliveryId, long timestamp) {
        Assignment current = read(shipperId).orElse(null);
        if (current != null && current.deliveryId() == deliveryId && timestamp >= current.timestamp()) {
            redis.delete(key(shipperId));
        }
    }

    public Optional<Long> activeDelivery(long shipperId) {
        return read(shipperId).map(Assignment::deliveryId);
    }

    private Optional<Assignment> read(long shipperId) {
        String value = redis.opsForValue().get(key(shipperId));
        if (value == null || value.isBlank()) return Optional.empty();
        String[] fields = value.split("\\|", 3);
        if (fields.length != 3) throw new IllegalStateException("Corrupt shipper assignment projection");
        return Optional.of(new Assignment(Long.parseLong(fields[0]), Long.parseLong(fields[1]), fields[2]));
    }

    private String encode(long deliveryId, long timestamp, String eventId) {
        return deliveryId + "|" + timestamp + "|" + eventId;
    }

    private String key(long shipperId) {
        return PREFIX + shipperId;
    }

    private record Assignment(long deliveryId, long timestamp, String eventId) {}
}
