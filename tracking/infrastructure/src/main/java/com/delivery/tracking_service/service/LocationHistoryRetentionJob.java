package com.delivery.tracking_service.service;
import com.delivery.tracking.application.api.LocationHistoryUseCase;
import com.delivery.tracking.application.api.LocationHistoryCleanupResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class LocationHistoryRetentionJob {
    private final LocationHistoryUseCase history;
    public LocationHistoryRetentionJob(LocationHistoryUseCase history) { this.history = history; }
    @Scheduled(cron = "${app.location-history.cleanup-cron:0 20 3 * * *}")
    public LocationHistoryCleanupResult cleanup() { return history.cleanupExpired(); }
}
