package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.PublisherLease;

public interface PublisherSessionUseCase {
    PublisherLease acquire(Long shipperId, String sessionId);
    boolean refreshIfCurrent(PublisherLease lease);
    void disconnected(PublisherLease lease);
    void sweepExpired(int batchSize);
}
