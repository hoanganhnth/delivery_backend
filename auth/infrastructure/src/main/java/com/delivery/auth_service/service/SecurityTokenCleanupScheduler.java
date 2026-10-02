package com.delivery.auth_service.service;
import com.delivery.auth.application.api.SecurityTokenUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
@RequiredArgsConstructor
public final class SecurityTokenCleanupScheduler {
    private final SecurityTokenUseCase security;
    @Scheduled(cron = "${app.security-token.cleanup-cron:0 35 3 * * *}")
    public void cleanup() {security.cleanup();}
}
