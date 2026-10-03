package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.PublisherLeaseIncidentPort;
import com.delivery.tracking.domain.PublisherLease;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PublisherLeaseLoggingAdapter implements PublisherLeaseIncidentPort {
    @Override public void graceFailed(PublisherLease lease, Exception failure) {
        log.error("Cannot mark shipper {} offline after publisher grace", lease.shipperId(), failure);
    }
    @Override public void sweepFailed(PublisherLease lease, Exception failure) {
        log.error("Cannot reconcile expired publisher lease for shipper {}", lease.shipperId(), failure);
    }
    @Override public void expiredOffline(PublisherLease lease) {
        log.info("Marked shipper {} offline after publisher lease expiry", lease.shipperId());
    }
}
