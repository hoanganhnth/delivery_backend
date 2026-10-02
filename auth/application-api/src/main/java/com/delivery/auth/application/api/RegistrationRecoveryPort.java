package com.delivery.auth.application.api;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RegistrationRecoveryPort {
    Optional<RegistrationRecoveryFacts> findByHandle(String rawHandle);
    void deleteExpiredBefore(LocalDateTime cutoff);
}
