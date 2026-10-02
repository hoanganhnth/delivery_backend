package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;
import java.time.LocalDateTime;

public record RegistrationRecoveryResult(Long principalId, AuthAccount.LifecycleStatus status,
        String nextAction, boolean profileLinked, LocalDateTime expiresAt) {}
