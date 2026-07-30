package com.delivery.tracking_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class LocationHistoryRetentionJob {

    private final LocationHistoryService history;
    private final int retentionDays;

    public LocationHistoryRetentionJob(
            LocationHistoryService history,
            @Value("${app.location-history.retention-days:90}") int retentionDays) {
        this.history = history;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(cron = "${app.location-history.cleanup-cron:0 20 3 * * *}")
    public LocationHistoryService.CleanupResult cleanup() {
        return history.cleanup(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    }
}
