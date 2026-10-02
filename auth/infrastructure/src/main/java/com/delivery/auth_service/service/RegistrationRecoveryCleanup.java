package com.delivery.auth_service.service;

import com.delivery.auth.application.api.RegistrationRecoveryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationRecoveryCleanup {
    private final RegistrationRecoveryUseCase recovery;
    @Scheduled(cron = "${app.identity.registration.handle-cleanup-cron:0 45 3 * * *}")
    public void cleanupExpiredHandles() { recovery.cleanupExpiredHandles(); }
}
