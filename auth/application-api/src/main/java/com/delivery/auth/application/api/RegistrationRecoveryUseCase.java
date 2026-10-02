package com.delivery.auth.application.api;

public interface RegistrationRecoveryUseCase {
    RegistrationRecoveryResult status(String rawHandle);
    void cleanupExpiredHandles();
}
