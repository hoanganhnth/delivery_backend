package com.delivery.tracking_service.service;

import com.delivery.tracking.application.api.PublisherSessionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Scheduling only; claim/fence/offline/retry decisions belong to application. */
@Service
@RequiredArgsConstructor
public class PublisherLeaseExpirySweeper {
    private final PublisherSessionUseCase publishers;

    @Value("${app.websocket.publisher.expiry-sweep-batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.websocket.publisher.expiry-sweep-interval-ms:5000}")
    public void sweep() {
        publishers.sweepExpired(batchSize);
    }
}
