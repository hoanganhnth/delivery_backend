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
