package com.delivery.auth.domain.policy;

import com.delivery.auth.domain.model.AuthAccount.LifecycleStatus;
import java.time.LocalDateTime;

public final class AccountLifecyclePolicy {
    private AccountLifecyclePolicy() {}
    public static LifecycleStatus status(Boolean active, Long profileId, Boolean verificationRequired,
            LocalDateTime verifiedAt) {
        if (!Boolean.TRUE.equals(active)) return LifecycleStatus.BLOCKED;
        if (profileId == null) return LifecycleStatus.PENDING_PROFILE;
        if (Boolean.TRUE.equals(verificationRequired) && verifiedAt == null) return LifecycleStatus.PENDING_EMAIL_VERIFICATION;
        return LifecycleStatus.ACTIVE;
    }
}
