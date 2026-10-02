package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;
import java.time.LocalDateTime;

/** Safe account projection for application results; password hashes are excluded. */
public record AccountSnapshot(
        Long id,
        Long userId,
        String email,
        AuthAccount.Role role,
        AuthAccount.LifecycleStatus lifecycleStatus,
        Boolean isActive,
        Boolean emailVerificationRequired,
        LocalDateTime emailVerifiedAt) {
}
