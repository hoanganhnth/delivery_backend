package com.delivery.tracking.domain;

/** Generation-fenced publisher ownership for a shipper's realtime stream. */
public record PublisherLease(long shipperId, String sessionId, long generation) {
    public PublisherLease {
        if (shipperId <= 0 || sessionId == null || sessionId.isBlank() || generation <= 0) {
            throw new IllegalArgumentException("positive shipper/generation and sessionId are required");
        }
    }

    public String redisValue() {
        return generation + ":" + sessionId;
    }
}
