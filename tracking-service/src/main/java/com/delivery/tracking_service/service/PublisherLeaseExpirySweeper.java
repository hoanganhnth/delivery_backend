package com.delivery.tracking_service.service;

import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository;
import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository.ExpiryClaim;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublisherLeaseExpirySweeper {

    private final ShipperPublisherLeaseRepository leaseRepository;
    private final ShipperAvailabilityService availabilityService;

    @Value("${app.websocket.publisher.expiry-sweep-batch-size:100}")
    private int batchSize;

    @Value("${app.websocket.publisher.expiry-claim-seconds:30}")
    private long claimSeconds;

    @Scheduled(fixedDelayString = "${app.websocket.publisher.expiry-sweep-interval-ms:5000}")
    public void sweep() {
        for (ExpiryClaim claim : leaseRepository.claimExpired(batchSize, claimSeconds)) {
            try {
                if (leaseRepository.shouldMarkOfflineAfterGrace(claim.lease())) {
                    availabilityService.markOffline(claim.lease().shipperId());
                    log.info("Marked shipper {} offline after publisher lease expiry",
                            claim.lease().shipperId());
                }
                leaseRepository.completeClaim(claim);
            } catch (Exception exception) {
                // Keep the claimed deadline. It becomes eligible again after the
                // claim timeout, so a process crash or transient Kafka/Redis error
                // cannot permanently lose the offline transition.
                log.error("Cannot reconcile expired publisher lease for shipper {}",
                        claim.lease().shipperId(), exception);
            }
        }
    }
}
