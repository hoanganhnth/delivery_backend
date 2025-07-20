package com.delivery.saga_orchestrator_service.saga.user_registration;

public enum UserSagaStatus {
    STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}