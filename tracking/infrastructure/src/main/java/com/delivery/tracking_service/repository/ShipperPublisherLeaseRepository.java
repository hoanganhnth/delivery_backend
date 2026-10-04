package com.delivery.tracking_service.repository;

import com.delivery.tracking.domain.PublisherLease;
import com.delivery.tracking.domain.PublisherExpiryClaim;
import com.delivery.tracking.application.api.PublisherLeaseStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.ArrayList;

@Repository
@RequiredArgsConstructor
public class ShipperPublisherLeaseRepository implements PublisherLeaseStorePort {

    static final String GENERATION_PREFIX = "tracking:publisher:generation:";
    static final String ACTIVE_PREFIX = "tracking:publisher:active:";
    static final String DEADLINES_KEY = "tracking:publisher:deadlines";

    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[2])
            if current then
              redis.call('ZREM', KEYS[3], ARGV[3] .. current)
            end
            local generation = redis.call('INCR', KEYS[1])
            redis.call('SET', KEYS[2], tostring(generation) .. ':' .. ARGV[1], 'EX', ARGV[2])
            local now = redis.call('TIME')
            local deadline = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000) + tonumber(ARGV[2]) * 1000
            redis.call('ZADD', KEYS[3], deadline, ARGV[3] .. tostring(generation) .. ':' .. ARGV[1])
            return generation
            """, Long.class);
    private static final DefaultRedisScript<Long> REFRESH = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            local now = redis.call('TIME')
            local deadline = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000) + tonumber(ARGV[2]) * 1000
            redis.call('ZADD', KEYS[2], deadline, ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
              return 0
            end
            redis.call('DEL', KEYS[1])
            local now = redis.call('TIME')
            local deadline = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000) + tonumber(ARGV[3]) * 1000
            redis.call('ZADD', KEYS[2], deadline, ARGV[2])
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
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now, 'LIMIT', 0, ARGV[1])
            if #expired == 0 then return {} end
            local claimUntil = now + tonumber(ARGV[2]) * 1000
            local claimed = {string.format('%.0f', claimUntil)}
            for _, member in ipairs(expired) do
              redis.call('ZADD', KEYS[1], claimUntil, member)
              table.insert(claimed, member)
            end
            return claimed
            """, List.class);
    private static final DefaultRedisScript<Long> COMPLETE_CLAIM = new DefaultRedisScript<>("""
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not score or tonumber(score) ~= tonumber(ARGV[2]) then
              return 0
            end
            local now = redis.call('TIME')
            if tonumber(score) <= tonumber(now[1]) * 1000 + tonumber(now[2]) / 1000 then
              return 0
            end
            return redis.call('ZREM', KEYS[1], ARGV[1])
            """, Long.class);
    private static final DefaultRedisScript<Long> CLAIM_IF_EXPIRED = new DefaultRedisScript<>("""
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            if not score or tonumber(score) > now then
              return 0
            end
            local claimUntil = now + tonumber(ARGV[2]) * 1000
            redis.call('ZADD', KEYS[1], claimUntil, ARGV[1])
            return claimUntil
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public PublisherLease acquire(Long shipperId, String sessionId, long leaseTtlSeconds) {
        Long generation = redisTemplate.execute(
                ACQUIRE,
                List.of(generationKey(shipperId), activeKey(shipperId), DEADLINES_KEY),
                sessionId,
                Long.toString(Math.max(1, leaseTtlSeconds)),
                shipperId + ":");
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
                deadlineMember(lease));
        return Long.valueOf(1L).equals(refreshed);
    }

    public boolean releaseForGraceIfCurrent(PublisherLease lease, long disconnectGraceSeconds) {
        Long released = redisTemplate.execute(
                RELEASE,
                List.of(activeKey(lease.shipperId()), DEADLINES_KEY),
                lease.redisValue(),
                deadlineMember(lease),
                Long.toString(Math.max(1, disconnectGraceSeconds)));
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
    public List<PublisherExpiryClaim> claimExpired(int limit, long claimSeconds) {
        List<String> members = (List<String>) redisTemplate.execute(
                CLAIM_EXPIRED,
                List.of(DEADLINES_KEY),
                Integer.toString(Math.max(1, limit)),
                Long.toString(Math.max(1, claimSeconds)));
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        long claimUntil = Long.parseLong(members.get(0));
        List<PublisherExpiryClaim> claims = new ArrayList<>(members.size() - 1);
        for (String member : members.subList(1, members.size())) {
            String[] parts = member.split(":", 3);
            if (parts.length != 3) {
                throw new IllegalStateException("Invalid publisher deadline member");
            }
            claims.add(new PublisherExpiryClaim(
                    new PublisherLease(
                            Long.parseLong(parts[0]), parts[2], Long.parseLong(parts[1])),
                    claimUntil));
        }
        return List.copyOf(claims);
    }

    public boolean completeClaim(PublisherExpiryClaim claim) {
        Long completed = redisTemplate.execute(
                COMPLETE_CLAIM,
                List.of(DEADLINES_KEY),
                deadlineMember(claim.lease()),
                Long.toString(claim.claimUntilEpochMillis()));
        return Long.valueOf(1L).equals(completed);
    }

    public PublisherExpiryClaim claimIfExpired(PublisherLease lease, long claimSeconds) {
        Long claimed = redisTemplate.execute(
                CLAIM_IF_EXPIRED,
                List.of(DEADLINES_KEY),
                deadlineMember(lease),
                Long.toString(Math.max(1, claimSeconds)));
        return claimed != null && claimed > 0 ? new PublisherExpiryClaim(lease, claimed) : null;
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
