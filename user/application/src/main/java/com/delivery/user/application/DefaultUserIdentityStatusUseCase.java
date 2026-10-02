package com.delivery.user.application;

import com.delivery.user.application.api.*;
import com.delivery.user.domain.UserIdentityLifecycleRules;
import java.time.LocalDateTime;
import java.util.Objects;

/** Auth-owned versioned lifecycle projection; transport receipts remain in infrastructure. */
public final class DefaultUserIdentityStatusUseCase implements UserIdentityStatusUseCase {
    private final UserIdentityStatusPort statuses;
    public DefaultUserIdentityStatusUseCase(UserIdentityStatusPort statuses) {
        this.statuses = Objects.requireNonNull(statuses, "statuses");
    }
    @Override public void apply(ApplyUserIdentityStatusCommand command) {
        Objects.requireNonNull(command, "command");
        statuses.mutate(command.principalId(), current -> {
            if (!UserIdentityLifecycleRules.shouldApply(current.version(), command.lifecycleVersion())) return current;
            boolean blocked = "BLOCKED".equals(command.status());
            return new UserIdentityProjection(command.status(), command.lifecycleVersion(), blocked, !blocked,
                    blocked ? LocalDateTime.now() : null, blocked ? command.changedByPrincipalId() : null,
                    blocked ? current.reason() : null);
        });
    }
}
