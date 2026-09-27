package com.delivery.auth.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Framework-independent authentication identity.
 *
 * <p>The fields intentionally retain the legacy user link and status-sync
 * metadata while the Auth/User migration is in progress. Persistence adapters
 * map this model to the existing {@code auth_account} table.
 */
public record AuthAccount(
        Long id,
        Long userId,
        LifecycleStatus lifecycleStatus,
        Long lifecycleVersion,
        String email,
        String passwordHash,
        Role role,
        Boolean isActive,
        Boolean emailVerificationRequired,
        LocalDateTime emailVerifiedAt,
        Boolean userStatusSyncPending,
        Long userStatusSyncVersion,
        Long userStatusSyncAdminId,
        String userStatusSyncBlockReason,
        Integer userStatusSyncAttempts,
        String userStatusSyncLastError,
        Boolean simulationActor,
        UUID simulationCohortId,
        UUID activeSimulationRunId,
        Long simulationBindingVersion,
        LocalDateTime userStatusSyncUpdatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** Roles understood by the Auth service. */
    public enum Role {
        USER,
        ADMIN,
        SHIPPER,
        SHOP_OWNER
    }

    /** Durable onboarding/account lifecycle owned by Auth. */
    public enum LifecycleStatus {
        PENDING_PROFILE,
        PENDING_EMAIL_VERIFICATION,
        ACTIVE,
        BLOCKED
    }

    /**
     * Login and refresh require every identity invariant owned by Auth to be
     * satisfied. The User profile link is deliberately fail-closed.
     */
    public boolean canAuthenticate() {
        return Boolean.TRUE.equals(isActive)
                && lifecycleStatus == LifecycleStatus.ACTIVE
                && userId != null
                && userId > 0
                && (!Boolean.TRUE.equals(emailVerificationRequired)
                        || emailVerifiedAt != null);
    }

    public boolean hasLinkedUser() {
        return userId != null && userId > 0;
    }

    public boolean hasVerifiedEmail() {
        return emailVerifiedAt != null;
    }
}
