package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.PublisherLease;

public interface PublisherLeaseIncidentPort {
    void graceFailed(PublisherLease lease, Exception failure);
    void sweepFailed(PublisherLease lease, Exception failure);
    void expiredOffline(PublisherLease lease);
}
