package com.delivery.auth.application.api;
public interface LifecycleTelemetryPort {
    void synchronizedStatus(Long userId, boolean blocked);
    void reconciliationFailed(Long accountId, RuntimeException failure);
}
