package com.delivery.api_gateway.ratelimit;

import java.util.List;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
class RedisFixedWindowRateLimitStore implements RateLimitStore {
    private static final DefaultRedisScript<List> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return {count, redis.call('TTL', KEYS[1])};",
            List.class);

    private final ReactiveStringRedisTemplate redis;

    RedisFixedWindowRateLimitStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Decision> increment(String key, int limit, long windowSeconds) {
        return redis.execute(INCREMENT_WITH_TTL, List.of(key), Long.toString(windowSeconds))
                .single()
                .map(result -> new Decision(((Number) result.get(0)).longValue(),
                        Math.max(1L, ((Number) result.get(1)).longValue())));
    }
}
