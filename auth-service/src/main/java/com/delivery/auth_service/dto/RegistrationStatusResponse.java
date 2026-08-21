package com.delivery.auth_service.dto;

import java.time.LocalDateTime;
import com.delivery.identity.contracts.IdentityLifecycleStatus;

public record RegistrationStatusResponse(
        Long principalId,
        IdentityLifecycleStatus status,
        String nextAction,
        boolean profileLinked,
        LocalDateTime expiresAt) { }
