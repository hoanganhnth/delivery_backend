package com.delivery.auth_service.service;
import com.delivery.auth.application.api.AccountLifecycleUseCase;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.RequiredArgsConstructor;
@Component
@RequiredArgsConstructor
public final class UserStatusSyncScheduler {
    private final AccountLifecycleUseCase lifecycle;
    @Scheduled(fixedDelayString = "${app.user-status-sync.poll-delay-ms:5000}")
    public void reconcile() { lifecycle.reconcile(); }
}
