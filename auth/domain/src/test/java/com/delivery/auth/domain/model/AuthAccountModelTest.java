package com.delivery.auth.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuthAccountModelTest {

    @Test
    void accountIsLoginEligibleOnlyWhenIdentityAndOnboardingAreComplete() {
        AuthAccount account = account(AuthAccount.LifecycleStatus.ACTIVE, true, false, true);

        assertTrue(account.canAuthenticate());
        assertTrue(account.hasLinkedUser());
        assertTrue(account.hasVerifiedEmail());
    }

    @Test
    void accountFailsClosedForMissingProfileEmailVerificationOrInactiveState() {
        assertFalse(account(AuthAccount.LifecycleStatus.PENDING_PROFILE, true, false, true)
                .canAuthenticate());
        assertFalse(account(AuthAccount.LifecycleStatus.ACTIVE, false, false, true)
                .canAuthenticate());
        assertFalse(account(AuthAccount.LifecycleStatus.ACTIVE, true, true, false)
                .canAuthenticate());
        assertTrue(account(AuthAccount.LifecycleStatus.ACTIVE, true, true, true)
                .canAuthenticate());
    }

    @Test
    void accountIdentityHelpersFailClosedForMissingValues() {
        AuthAccount account = new AuthAccount(
                7L, null, AuthAccount.LifecycleStatus.ACTIVE, 2L,
                "user@example.com", "hash", AuthAccount.Role.USER, true, false,
                null, false, 0L, null, null, 0, null, false, null, null, 0L,
                null, null, null);

        assertFalse(account.canAuthenticate());
        assertFalse(account.hasLinkedUser());
        assertFalse(account.hasVerifiedEmail());
    }

    @Test
    void simulationBindingRequiresBothActorFlagAndAnActiveRun() {
        java.util.UUID run = java.util.UUID.randomUUID();
        assertFalse(simulationAccount(null, run).hasActiveSimulationBinding());
        assertFalse(simulationAccount(false, run).hasActiveSimulationBinding());
        assertFalse(simulationAccount(true, null).hasActiveSimulationBinding());
        assertTrue(simulationAccount(true, run).hasActiveSimulationBinding());
    }

    @Test
    void provisioningAdvancesTheVersionAndRetainsSimulationAndSecurityFacts() {
        AuthAccount before = simulationAccount(true, java.util.UUID.randomUUID());
        AuthAccount after = before.withProvisionedProfile(55L);
        org.junit.jupiter.api.Assertions.assertEquals(55L, after.userId());
        org.junit.jupiter.api.Assertions.assertEquals(2L, after.lifecycleVersion());
        org.junit.jupiter.api.Assertions.assertEquals(before.activeSimulationRunId(), after.activeSimulationRunId());
        org.junit.jupiter.api.Assertions.assertEquals(before.passwordHash(), after.passwordHash());
        org.junit.jupiter.api.Assertions.assertEquals(11L, before.userId());
        AuthAccount unversioned = new AuthAccount(7L, null, AuthAccount.LifecycleStatus.PENDING_PROFILE, null,
                "user@example.com", "hash", AuthAccount.Role.USER, true, true, null,
                false, 0L, null, null, 0, null, false, null, null, 0L, null, null, null);
        var linked = unversioned.withProvisionedProfile(55L);
        org.junit.jupiter.api.Assertions.assertEquals(1L, linked.lifecycleVersion());
        org.junit.jupiter.api.Assertions.assertEquals(AuthAccount.LifecycleStatus.ACTIVE, linked.lifecycleStatus());
        assertTrue(linked.emailVerificationRequired());
    }

    @Test
    void verifiedSocialEmailRetainsLifecycleProfileAndSimulationBinding() {
        var before = simulationAccount(true, java.util.UUID.randomUUID());
        var now = LocalDateTime.now();
        var after = before.withVerifiedEmail(now);
        org.junit.jupiter.api.Assertions.assertEquals(now, after.emailVerifiedAt());
        assertFalse(after.emailVerificationRequired());
        org.junit.jupiter.api.Assertions.assertEquals(before.userId(), after.userId());
        org.junit.jupiter.api.Assertions.assertEquals(before.lifecycleVersion(), after.lifecycleVersion());
        org.junit.jupiter.api.Assertions.assertEquals(before.lifecycleStatus(), after.lifecycleStatus());
        org.junit.jupiter.api.Assertions.assertEquals(before.activeSimulationRunId(), after.activeSimulationRunId());
    }

    @Test
    void simulationBindingCopiesAllIdentityFactsAndReleaseOnlyClearsTheRun() {
        var before=simulationAccount(true,null);
        var run=java.util.UUID.randomUUID();var cohort=java.util.UUID.randomUUID();
        var bound=before.withSimulationBinding(run,cohort,4L);
        org.junit.jupiter.api.Assertions.assertEquals(run,bound.activeSimulationRunId());
        org.junit.jupiter.api.Assertions.assertEquals(cohort,bound.simulationCohortId());
        org.junit.jupiter.api.Assertions.assertEquals(4L,bound.simulationBindingVersion());
        org.junit.jupiter.api.Assertions.assertEquals(before.passwordHash(),bound.passwordHash());
        org.junit.jupiter.api.Assertions.assertEquals(before.lifecycleVersion(),bound.lifecycleVersion());
        org.junit.jupiter.api.Assertions.assertEquals(before.emailVerifiedAt(),bound.emailVerifiedAt());
        org.junit.jupiter.api.Assertions.assertEquals(before.userStatusSyncPending(),bound.userStatusSyncPending());
        org.junit.jupiter.api.Assertions.assertEquals(before.userId(),bound.userId());
        assertFalse(before.hasActiveSimulationBinding());assertTrue(bound.hasActiveSimulationBinding());
        var released=bound.withoutSimulationBinding(5L);
        org.junit.jupiter.api.Assertions.assertNull(released.activeSimulationRunId());
        org.junit.jupiter.api.Assertions.assertEquals(cohort,released.simulationCohortId());
        org.junit.jupiter.api.Assertions.assertEquals(5L,released.simulationBindingVersion());
        org.junit.jupiter.api.Assertions.assertEquals(before.passwordHash(),released.passwordHash());
        assertFalse(released.hasActiveSimulationBinding());
    }

    @Test
    void passwordResetAndVerificationLifecycleKeepOtherIdentityAndSimulationFacts() {
        var before=simulationAccount(true,java.util.UUID.randomUUID());
        var passwordChanged=before.withPasswordHash("new-bcrypt-hash");
        org.junit.jupiter.api.Assertions.assertEquals("new-bcrypt-hash",passwordChanged.passwordHash());
        org.junit.jupiter.api.Assertions.assertEquals(before.id(),passwordChanged.id());
        org.junit.jupiter.api.Assertions.assertEquals(before.activeSimulationRunId(),passwordChanged.activeSimulationRunId());
        org.junit.jupiter.api.Assertions.assertEquals(before.lifecycleStatus(),passwordChanged.lifecycleStatus());
        org.junit.jupiter.api.Assertions.assertEquals(before.userStatusSyncVersion(),passwordChanged.userStatusSyncVersion());
        org.junit.jupiter.api.Assertions.assertEquals("hash",before.passwordHash());
        var blocked=passwordChanged.withLifecycleStatus(AuthAccount.LifecycleStatus.BLOCKED);
        org.junit.jupiter.api.Assertions.assertEquals(2L,blocked.lifecycleVersion());
        org.junit.jupiter.api.Assertions.assertEquals(before.simulationCohortId(),blocked.simulationCohortId());
        org.junit.jupiter.api.Assertions.assertEquals("new-bcrypt-hash",blocked.passwordHash());
        org.junit.jupiter.api.Assertions.assertEquals(2L,blocked.withLifecycleStatus(AuthAccount.LifecycleStatus.BLOCKED).lifecycleVersion());
        var unversioned=new AuthAccount(7L,11L,AuthAccount.LifecycleStatus.PENDING_EMAIL_VERIFICATION,null,
                "user@example.com","hash",AuthAccount.Role.USER,true,true,null,false,0L,null,null,0,null,
                false,null,null,0L,null,null,null);
        org.junit.jupiter.api.Assertions.assertEquals(1L,
                unversioned.withLifecycleStatus(AuthAccount.LifecycleStatus.ACTIVE).lifecycleVersion());
    }

    private static AuthAccount simulationAccount(Boolean actor, java.util.UUID run) {
        return new AuthAccount(7L, 11L, AuthAccount.LifecycleStatus.ACTIVE, 1L,
                "user@example.com", "hash", AuthAccount.Role.USER, true, false, null,
                false, 0L, null, null, 0, null, actor, java.util.UUID.randomUUID(), run, 1L,
                null, null, null);
    }

    private static AuthAccount account(
            AuthAccount.LifecycleStatus lifecycle,
            boolean active,
            boolean verificationRequired,
            boolean verified) {
        return new AuthAccount(
                7L, 11L, lifecycle, 2L, "user@example.com", "hash",
                AuthAccount.Role.USER, active, verificationRequired,
                verified ? LocalDateTime.now() : null, false, 0L, null, null,
                0, null, false, null, null, 0L, null, null, null);
    }
}
