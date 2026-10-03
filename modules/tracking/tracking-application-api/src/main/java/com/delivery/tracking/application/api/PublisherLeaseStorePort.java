package com.delivery.tracking.application.api;

import com.delivery.tracking.domain.PublisherLease;
import com.delivery.tracking.domain.PublisherExpiryClaim;
import java.util.List;

public interface PublisherLeaseStorePort {
    PublisherLease acquire(Long shipperId, String sessionId, long leaseTtlSeconds);
    boolean refreshIfCurrent(PublisherLease lease, long leaseTtlSeconds);
    boolean releaseForGraceIfCurrent(PublisherLease lease, long disconnectGraceSeconds);
    boolean shouldMarkOfflineAfterGrace(PublisherLease lease);
    List<PublisherExpiryClaim> claimExpired(int limit, long claimSeconds);
    PublisherExpiryClaim claimIfExpired(PublisherLease lease, long claimSeconds);
    boolean completeClaim(PublisherExpiryClaim claim);
}
