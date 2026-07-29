package com.delivery.tracking_service.websocket;

public record PublisherLease(Long shipperId, String sessionId, long generation) {
    public PublisherLease {
        if (shipperId == null || shipperId <= 0 || sessionId == null || sessionId.isBlank()
                || generation <= 0) {
            throw new IllegalArgumentException("positive shipper/generation and sessionId are required");
        }
    }

    public String redisValue() {
        return generation + ":" + sessionId;
    }
}
