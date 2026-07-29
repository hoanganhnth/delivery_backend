package com.delivery.tracking_service.repository;

import com.delivery.tracking_service.websocket.PublisherLease;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.ArrayList;

@Repository
@RequiredArgsConstructor
public class ShipperPublisherLeaseRepository {

    private static final String GENERATION_PREFIX = "tracking:publisher:generation:";
    private static final String ACTIVE_PREFIX = "tracking:publisher:active:";
    private static final String DEADLINES_KEY = "tracking:publisher:deadlines";

    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[2])
            if current then
              redis.call('ZREM', KEYS[3], ARGV[3] .. current)
            end
            local generation = redis.call('INCR', KEYS[1])
            redis.call('SET', KEYS[2], tostring(generation) .. ':' .. ARGV[1], 'EX', ARGV[2])
            redis.call('ZADD', KEYS[3], ARGV[4], ARGV[3] .. tostring(generation) .. ':' .. ARGV[1])
            return generation
            """, Long.class);
    private static final DefaultRedisScript<Long> REFRESH = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> SHOULD_MARK_OFFLINE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
              return 0
            end
            return 1
            """, Long.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CLAIM_EXPIRED = new DefaultRedisScript<>("""
            local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
            for _, member in ipairs(expired) do
              redis.call('ZADD', KEYS[1], ARGV[3], member)
            end
            return expired
            """, List.class);
    private static final DefaultRedisScript<Long> COMPLETE_CLAIM = new DefaultRedisScript<>("""
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not score or tonumber(score) ~= tonumber(ARGV[2]) then
              return 0
            end
            return redis.call('ZREM', KEYS[1], ARGV[1])
            """, Long.class);
    private static final DefaultRedisScript<Long> CLAIM_IF_EXPIRED = new DefaultRedisScript<>("""
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not score or tonumber(score) > tonumber(ARGV[2]) then
              return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public PublisherLease acquire(Long shipperId, String sessionId, long leaseTtlSeconds) {
        Long generation = redisTemplate.execute(
                ACQUIRE,
                List.of(generationKey(shipperId), activeKey(shipperId), DEADLINES_KEY),
                sessionId,
                Long.toString(Math.max(1, leaseTtlSeconds)),
                shipperId + ":",
                Long.toString(deadline(leaseTtlSeconds)));
        if (generation == null || generation <= 0) {
            throw new IllegalStateException("Cannot acquire shipper publisher generation");
        }
        return new PublisherLease(shipperId, sessionId, generation);
    }

    public boolean refreshIfCurrent(PublisherLease lease, long leaseTtlSeconds) {
        Long refreshed = redisTemplate.execute(
                REFRESH,
                List.of(activeKey(lease.shipperId()), DEADLINES_KEY),
                lease.redisValue(),
                Long.toString(Math.max(1, leaseTtlSeconds)),
                Long.toString(deadline(leaseTtlSeconds)),
                deadlineMember(lease));
        return Long.valueOf(1L).equals(refreshed);
    }

    public boolean releaseForGraceIfCurrent(PublisherLease lease, long disconnectGraceSeconds) {
        Long released = redisTemplate.execute(
                RELEASE,
                List.of(activeKey(lease.shipperId()), DEADLINES_KEY),
                lease.redisValue(),
                deadlineMember(lease),
                Long.toString(deadline(Math.max(0, disconnectGraceSeconds))));
        return Long.valueOf(1L).equals(released);
    }

    public boolean shouldMarkOfflineAfterGrace(PublisherLease lease) {
        Long shouldMarkOffline = redisTemplate.execute(
                SHOULD_MARK_OFFLINE,
                List.of(generationKey(lease.shipperId()), activeKey(lease.shipperId())),
                Long.toString(lease.generation()));
        return Long.valueOf(1L).equals(shouldMarkOffline);
    }

    @SuppressWarnings("unchecked")
    public List<ExpiryClaim> claimExpired(int limit, long claimSeconds) {
        long claimUntil = deadline(Math.max(1, claimSeconds));
        List<String> members = (List<String>) redisTemplate.execute(
                CLAIM_EXPIRED,
                List.of(DEADLINES_KEY),
                Long.toString(System.currentTimeMillis()),
                Integer.toString(Math.max(1, limit)),
                Long.toString(claimUntil));
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<ExpiryClaim> claims = new ArrayList<>(members.size());
        for (String member : members) {
            String[] parts = member.split(":", 3);
            if (parts.length != 3) {
                throw new IllegalStateException("Invalid publisher deadline member");
            }
            claims.add(new ExpiryClaim(
                    new PublisherLease(
                            Long.parseLong(parts[0]), parts[2], Long.parseLong(parts[1])),
                    claimUntil));
        }
        return List.copyOf(claims);
    }

    public boolean completeClaim(ExpiryClaim claim) {
        Long completed = redisTemplate.execute(
                COMPLETE_CLAIM,
                List.of(DEADLINES_KEY),
                deadlineMember(claim.lease()),
                Long.toString(claim.claimUntilEpochMillis()));
        return Long.valueOf(1L).equals(completed);
    }

    public ExpiryClaim claimIfExpired(PublisherLease lease, long claimSeconds) {
        long claimUntil = deadline(Math.max(1, claimSeconds));
        Long claimed = redisTemplate.execute(
                CLAIM_IF_EXPIRED,
                List.of(DEADLINES_KEY),
                deadlineMember(lease),
                Long.toString(System.currentTimeMillis()),
                Long.toString(claimUntil));
        return Long.valueOf(1L).equals(claimed) ? new ExpiryClaim(lease, claimUntil) : null;
    }

    public record ExpiryClaim(PublisherLease lease, long claimUntilEpochMillis) {}

    private long deadline(long seconds) {
        return System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
    }

    private String deadlineMember(PublisherLease lease) {
        return lease.shipperId() + ":" + lease.redisValue();
    }

    private String generationKey(Long shipperId) {
        return GENERATION_PREFIX + shipperId;
    }

    private String activeKey(Long shipperId) {
        return ACTIVE_PREFIX + shipperId;
    }
}
