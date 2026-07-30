package com.delivery.api_gateway.ratelimit;

import reactor.core.publisher.Mono;

public interface RateLimitStore {
    Mono<Decision> increment(String key, int limit, long windowSeconds);

    record Decision(long count, long retryAfterSeconds) {
        public boolean allowed(int limit) { return count <= limit; }
    }
}
