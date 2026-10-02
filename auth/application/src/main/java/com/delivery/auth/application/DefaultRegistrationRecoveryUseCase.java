package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.policy.AuthResourceMissing;
import java.time.*;
import java.util.Objects;

public final class DefaultRegistrationRecoveryUseCase implements RegistrationRecoveryUseCase {
    private final RegistrationRecoveryPort handles;
    private final int retentionDays;
    private final Clock clock;
    public DefaultRegistrationRecoveryUseCase(RegistrationRecoveryPort handles, int retentionDays, Clock clock) {
        this.handles = Objects.requireNonNull(handles);
        this.retentionDays = Math.max(0, retentionDays);
        this.clock = Objects.requireNonNull(clock);
    }
    @Override public RegistrationRecoveryResult status(String rawHandle) {
        if (rawHandle == null || rawHandle.isBlank()) throw new IllegalArgumentException("Registration handle is required");
        var recovery = handles.findByHandle(rawHandle)
                .orElseThrow(() -> new AuthResourceMissing("Registration not found with handle: not found"));
        if (!recovery.expiresAt().isAfter(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("Registration handle expired");
        }
        var account = recovery.account();
        String next = switch (account.lifecycleStatus()) {
            case PENDING_PROFILE -> "CREATE_PROFILE";
            case PENDING_EMAIL_VERIFICATION -> "VERIFY_EMAIL";
            case ACTIVE -> "LOGIN";
            case BLOCKED -> "CONTACT_SUPPORT";
        };
        return new RegistrationRecoveryResult(account.id(), account.lifecycleStatus(), next,
                account.userId() != null, recovery.expiresAt());
    }
    @Override public void cleanupExpiredHandles() {
        handles.deleteExpiredBefore(LocalDateTime.now(clock).minusDays(retentionDays));
    }
}
