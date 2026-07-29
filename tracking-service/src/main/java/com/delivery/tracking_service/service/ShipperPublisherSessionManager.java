package com.delivery.tracking_service.service;

import com.delivery.tracking_service.dto.response.ShipperLocationResponse;
import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository;
import com.delivery.tracking_service.repository.ShipperPublisherLeaseRepository.ExpiryClaim;
import com.delivery.tracking_service.websocket.PublisherLease;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.function.Consumer;

@Service
@Slf4j
public class ShipperPublisherSessionManager {

    private final ShipperPublisherLeaseRepository leaseRepository;
    private final ShipperAvailabilityService availabilityService;
    private final TaskScheduler scheduler;
    private final long disconnectGraceSeconds;
    private final long leaseTtlSeconds;
    private final long claimSeconds;

    public ShipperPublisherSessionManager(
            ShipperPublisherLeaseRepository leaseRepository,
            ShipperAvailabilityService availabilityService,
            @Qualifier("publisherGraceTaskScheduler") TaskScheduler scheduler,
            @Value("${app.websocket.publisher.disconnect-grace-seconds:30}") long disconnectGraceSeconds,
            @Value("${app.websocket.publisher.lease-ttl-seconds:120}") long leaseTtlSeconds,
            @Value("${app.websocket.publisher.expiry-claim-seconds:30}") long claimSeconds) {
        this.leaseRepository = leaseRepository;
        this.availabilityService = availabilityService;
        this.scheduler = scheduler;
        this.disconnectGraceSeconds = Math.max(0, disconnectGraceSeconds);
        this.leaseTtlSeconds = Math.max(this.disconnectGraceSeconds + 1, leaseTtlSeconds);
        this.claimSeconds = Math.max(1, claimSeconds);
    }

    public PublisherLease acquire(Long shipperId, String sessionId) {
        return leaseRepository.acquire(shipperId, sessionId, leaseTtlSeconds);
    }

    public boolean refreshIfCurrent(PublisherLease lease) {
        return leaseRepository.refreshIfCurrent(lease, leaseTtlSeconds);
    }

    public void disconnected(
            PublisherLease lease,
            Consumer<ShipperLocationResponse> afterOffline) {
        if (!leaseRepository.releaseForGraceIfCurrent(lease, disconnectGraceSeconds)) {
            return;
        }
        scheduler.schedule(
                () -> markOfflineIfGenerationStillDisconnected(lease, afterOffline),
                Instant.now().plusSeconds(disconnectGraceSeconds));
    }

    private void markOfflineIfGenerationStillDisconnected(
            PublisherLease lease,
            Consumer<ShipperLocationResponse> afterOffline) {
        try {
            ExpiryClaim claim = leaseRepository.claimIfExpired(lease, claimSeconds);
            if (claim == null) {
                return;
            }
            if (!leaseRepository.shouldMarkOfflineAfterGrace(lease)) {
                leaseRepository.completeClaim(claim);
                return;
            }
            ShipperLocationResponse offline = availabilityService.markOffline(lease.shipperId());
            afterOffline.accept(offline);
            leaseRepository.completeClaim(claim);
        } catch (Exception exception) {
            log.error("Cannot mark shipper {} offline after publisher grace", lease.shipperId(), exception);
        }
    }
}
