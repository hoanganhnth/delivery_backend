package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.PublisherLease;
import java.util.function.Consumer;

public interface PublisherSessionUseCase {
    PublisherLease acquire(Long shipperId, String sessionId);
    boolean refreshIfCurrent(PublisherLease lease);
    void disconnected(PublisherLease lease, Consumer<OfflineShipperLocation> afterOffline);
    void sweepExpired(int batchSize);
}
