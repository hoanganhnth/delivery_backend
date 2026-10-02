package com.delivery.auth_service.service;
import com.delivery.auth.application.api.LifecycleTelemetryPort;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
@Component
@Slf4j
public final class LifecycleLoggingAdapter implements LifecycleTelemetryPort {
    public void synchronizedStatus(Long userId, boolean blocked) {
        log.info("Synchronized user profile block state userId={}, blocked={}", userId, blocked);
    }
    public void reconciliationFailed(Long accountId, RuntimeException failure) {
        log.warn("Pending user profile status sync failed for authAccountId={}", accountId, failure);
    }
}
