package com.delivery.tracking_service.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/** Shared Redis routing projection derived from durable shipper status events. */
@Repository
public class ShipperDeliveryAssignmentStore implements com.delivery.tracking.application.api.DeliveryRoomAssignmentPort {

    private static final String PREFIX = "tracking:shipper:active-delivery:";
    private static final String BATCH_PREFIX = "tracking:shipper:active-deliveries:";
    private static final String BATCH_FENCE_PREFIX = "tracking:shipper:active-delivery-fence:";
    private static final String TERMINAL_PREFIX = "tracking:shipper:assignment-terminal:";
    private static final String BATCH_TERMINAL_PREFIX = "tracking:shipper:batch-assignment-terminal:";
    private static final Duration TTL = Duration.ofHours(24);
    /**
     * Kafka partitions prevent duplicate execution only inside one consumer
     * group. The routing projection is shared by Tracking replicas, so its
     * stale-event fence must be evaluated and written atomically in Redis.
     */
    private static final DefaultRedisScript<Long> APPLY_BUSY = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            local incomingTimestamp = tonumber(ARGV[2])
            local terminal = redis.call('GET', KEYS[2])
            if terminal then
              local terminalTimestamp = tonumber(terminal)
              if not terminalTimestamp then return -2 end
              if terminalTimestamp >= incomingTimestamp then return 0 end
            end
            if current then
              local first = string.find(current, '|')
              local second = first and string.find(current, '|', first + 1) or nil
              if not second then return -2 end
              local currentDelivery = string.sub(current, 1, first - 1)
              local currentTimestamp = tonumber(string.sub(current, first + 1, second - 1))
              local currentEventId = string.sub(current, second + 1)
              if not currentTimestamp then return -2 end
              if currentTimestamp > incomingTimestamp then return 0 end
              if currentTimestamp == incomingTimestamp then
                if currentDelivery == ARGV[1] and currentEventId == ARGV[3] then return 0 end
                return -1
              end
            end
            redis.call('SET', KEYS[1], ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3], 'EX', tonumber(ARGV[4]))
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> APPLY_AVAILABLE = new DefaultRedisScript<>("""
            local incoming = tonumber(ARGV[2])
            local terminal = redis.call('GET', KEYS[2])
            local terminalTimestamp = terminal and tonumber(terminal) or nil
            if terminal and not terminalTimestamp then return -2 end
            if terminalTimestamp and terminalTimestamp > incoming then return 0 end
            local current = redis.call('GET', KEYS[1])
            if current then
              local first = string.find(current, '|')
              local second = first and string.find(current, '|', first + 1) or nil
              if not second then return -2 end
              local currentDelivery = string.sub(current, 1, first - 1)
              local currentTimestamp = tonumber(string.sub(current, first + 1, second - 1))
              if not currentTimestamp then return -2 end
              if currentDelivery ~= ARGV[1] or currentTimestamp > incoming then return 0 end
            elseif terminalTimestamp and terminalTimestamp == incoming then return 0 end
            redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[3]))
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;

    private static final DefaultRedisScript<Long> APPLY_BATCH_BUSY = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[2])
            local incoming = tonumber(ARGV[2])
            local terminal = redis.call('GET', KEYS[3])
            if terminal then
              local terminalTimestamp = tonumber(terminal)
              if not terminalTimestamp then return -2 end
              if terminalTimestamp >= incoming then return 0 end
            end
            if current then
              local sep = string.find(current, '|')
              if not sep then return -2 end
              local timestamp = tonumber(string.sub(current, 1, sep - 1))
              local eventId = string.sub(current, sep + 1)
              if not timestamp then return -2 end
              if timestamp > incoming then return 0 end
              if timestamp == incoming then
                if eventId == ARGV[3] then return 0 end
                return -1
              end
            end
            redis.call('SET', KEYS[2], ARGV[2] .. '|' .. ARGV[3], 'EX', tonumber(ARGV[4]))
            redis.call('SADD', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> APPLY_BATCH_AVAILABLE = new DefaultRedisScript<>("""
            local incoming = tonumber(ARGV[2])
            local terminal = redis.call('GET', KEYS[3])
            local terminalTimestamp = terminal and tonumber(terminal) or nil
            if terminal and not terminalTimestamp then return -2 end
            if terminalTimestamp and terminalTimestamp > incoming then return 0 end
            local current = redis.call('GET', KEYS[2])
            if current then
              local sep = string.find(current, '|')
              if not sep then return -2 end
              local timestamp = tonumber(string.sub(current, 1, sep - 1))
              if not timestamp then return -2 end
              if timestamp > incoming then return 0 end
            elseif terminalTimestamp and terminalTimestamp == incoming then return 0 end
            redis.call('SET', KEYS[3], ARGV[2], 'EX', tonumber(ARGV[3]))
            redis.call('DEL', KEYS[2])
            redis.call('SREM', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    public ShipperDeliveryAssignmentStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void busy(long shipperId, long deliveryId, long timestamp, String eventId) {
        if (shipperId <= 0 || deliveryId <= 0 || timestamp <= 0 || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("positive assignment identity/timestamp and eventId are required");
        }
        Long result = redis.execute(APPLY_BUSY, List.of(key(shipperId), terminalKey(shipperId)), Long.toString(deliveryId),
                Long.toString(timestamp), eventId, Long.toString(TTL.toSeconds()));
        if (result == null || result == -2L) {
            throw new IllegalStateException("Corrupt shipper assignment projection");
        }
        if (result == -1L) {
            throw new IllegalArgumentException("Conflicting shipper assignment events share the same timestamp");
        }
    }

    public void available(long shipperId, long deliveryId, long timestamp) {
        if (shipperId <= 0 || deliveryId <= 0 || timestamp <= 0) {
            throw new IllegalArgumentException("positive assignment identity and timestamp are required");
        }
        Long result = redis.execute(APPLY_AVAILABLE, List.of(key(shipperId), terminalKey(shipperId)), Long.toString(deliveryId),
                Long.toString(timestamp), Long.toString(TTL.toSeconds()));
        if (result == null || result == -2L) {
            throw new IllegalStateException("Corrupt shipper assignment projection");
        }
    }

    public void busyBatch(long shipperId, long deliveryId, long timestamp, String eventId) {
        validate(shipperId, deliveryId, timestamp, eventId);
        Long result = redis.execute(APPLY_BATCH_BUSY,
                List.of(batchKey(shipperId), batchFenceKey(shipperId, deliveryId), batchTerminalKey(shipperId, deliveryId)),
                Long.toString(deliveryId), Long.toString(timestamp), eventId, Long.toString(TTL.toSeconds()));
        if (result == null || result == -2L) throw new IllegalStateException("Corrupt batch assignment projection");
        if (result == -1L) throw new IllegalArgumentException("Conflicting batch assignment events share the same timestamp");
    }

    public void availableBatch(long shipperId, long deliveryId, long timestamp) {
        if (shipperId <= 0 || deliveryId <= 0 || timestamp <= 0) {
            throw new IllegalArgumentException("positive batch assignment identity and timestamp are required");
        }
        Long result = redis.execute(APPLY_BATCH_AVAILABLE,
                List.of(batchKey(shipperId), batchFenceKey(shipperId, deliveryId), batchTerminalKey(shipperId, deliveryId)),
                Long.toString(deliveryId), Long.toString(timestamp), Long.toString(TTL.toSeconds()));
        if (result == null || result == -2L) throw new IllegalStateException("Corrupt batch assignment projection");
    }

    public Optional<Long> activeDelivery(long shipperId) {
        Set<Long> batch = activeDeliveries(shipperId);
        if (!batch.isEmpty()) return batch.stream().sorted().findFirst();
        return read(shipperId).map(Assignment::deliveryId);
    }

    public Set<Long> activeDeliveries(long shipperId) {
        Set<String> members = redis.opsForSet() == null ? null : redis.opsForSet().members(batchKey(shipperId));
        Set<Long> result = new HashSet<>();
        if (members != null) {
            members.forEach(member -> {
                try { result.add(Long.parseLong(member)); } catch (NumberFormatException ignored) { }
            });
        }
        read(shipperId).map(Assignment::deliveryId).ifPresent(result::add);
        return result;
    }

    private void validate(long shipperId, long deliveryId, long timestamp, String eventId) {
        if (shipperId <= 0 || deliveryId <= 0 || timestamp <= 0 || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("positive assignment identity/timestamp and eventId are required");
        }
    }

    private Optional<Assignment> read(long shipperId) {
        String value = redis.opsForValue().get(key(shipperId));
        if (value == null || value.isBlank()) return Optional.empty();
        String[] fields = value.split("\\|", 3);
        if (fields.length != 3) throw new IllegalStateException("Corrupt shipper assignment projection");
        return Optional.of(new Assignment(Long.parseLong(fields[0]), Long.parseLong(fields[1]), fields[2]));
    }

    private String key(long shipperId) {
        return PREFIX + shipperId;
    }

    private String terminalKey(long shipperId) { return TERMINAL_PREFIX + shipperId; }
    private String batchTerminalKey(long shipperId, long deliveryId) { return BATCH_TERMINAL_PREFIX + shipperId + ":" + deliveryId; }

    private String batchKey(long shipperId) { return BATCH_PREFIX + shipperId; }

    private String batchFenceKey(long shipperId, long deliveryId) {
        return BATCH_FENCE_PREFIX + shipperId + ":" + deliveryId;
    }

    private record Assignment(long deliveryId, long timestamp, String eventId) {}
}
