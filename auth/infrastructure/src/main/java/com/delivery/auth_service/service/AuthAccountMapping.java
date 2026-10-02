package com.delivery.auth_service.service;

import com.delivery.auth.domain.model.AuthAccount;

/** Complete persistence-to-core mapping; no identity policy is evaluated here. */
final class AuthAccountMapping {
    private AuthAccountMapping() {}
    static AuthAccount toDomain(com.delivery.auth_service.entity.AuthAccount a) {
        return new AuthAccount(a.getId(), a.getUserId(), AuthAccount.LifecycleStatus.valueOf(a.getLifecycleStatus().name()),
                a.getLifecycleVersion(), a.getEmail(), a.getPasswordHash(), AuthAccount.Role.valueOf(a.getRole().name()),
                a.getIsActive(), a.getEmailVerificationRequired(), a.getEmailVerifiedAt(), a.getUserStatusSyncPending(),
                a.getUserStatusSyncVersion(), a.getUserStatusSyncAdminId(), a.getUserStatusSyncBlockReason(),
                a.getUserStatusSyncAttempts(), a.getUserStatusSyncLastError(), a.getSimulationActor(), a.getSimulationCohortId(),
                a.getActiveSimulationRunId(), a.getSimulationBindingVersion(), a.getUserStatusSyncUpdatedAt(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
    static void apply(AuthAccount a, com.delivery.auth_service.entity.AuthAccount entity) {
        entity.setUserId(a.userId());
        entity.setLifecycleStatus(com.delivery.identity.contracts.IdentityLifecycleStatus.valueOf(a.lifecycleStatus().name()));
        entity.setLifecycleVersion(a.lifecycleVersion()); entity.setEmail(a.email()); entity.setPasswordHash(a.passwordHash());
        entity.setRole(com.delivery.auth_service.entity.AuthAccount.Role.valueOf(a.role().name()));
        entity.setIsActive(a.isActive()); entity.setEmailVerificationRequired(a.emailVerificationRequired());
        entity.setEmailVerifiedAt(a.emailVerifiedAt()); entity.setUserStatusSyncPending(a.userStatusSyncPending());
        entity.setUserStatusSyncVersion(a.userStatusSyncVersion()); entity.setUserStatusSyncAdminId(a.userStatusSyncAdminId());
        entity.setUserStatusSyncBlockReason(a.userStatusSyncBlockReason()); entity.setUserStatusSyncAttempts(a.userStatusSyncAttempts());
        entity.setUserStatusSyncLastError(a.userStatusSyncLastError()); entity.setSimulationActor(a.simulationActor());
        entity.setSimulationCohortId(a.simulationCohortId()); entity.setActiveSimulationRunId(a.activeSimulationRunId());
        entity.setSimulationBindingVersion(a.simulationBindingVersion()); entity.setUserStatusSyncUpdatedAt(a.userStatusSyncUpdatedAt());
    }
}
