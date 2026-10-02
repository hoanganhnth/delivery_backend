package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.*;
import com.delivery.auth.domain.policy.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RefreshWorkflowTest {
    @Test void rotatesUnderLockAndKeepsFamilyAndSevenDaySession() {
        Fake f = new Fake();
        var result = f.useCase().refresh(new RefreshTokenCommand("old"));
        assertThat(result.refreshToken()).isEqualTo("new");
        assertThat(result.authId()).isEqualTo(7L);
        assertThat(f.operations).containsExactly("verify", "lock", "family", "access", "refresh:family", "rotate", "remember", "activity", "commit");
        assertThat(f.expiry).isEqualTo(f.rotatedAt.plusDays(7));
    }
    @Test void rejectsInvalidOrMissingCredentialBeforeMutation() {
        Fake f = new Fake(); f.valid = false; Fake invalidFirst = f;
        assertThatThrownBy(() -> invalidFirst.useCase().refresh(new RefreshTokenCommand("old")))
                .isInstanceOf(InvalidAuthToken.class).hasMessage("Invalid refresh token");
        assertThat(f.operations).containsExactly("verify", "rollback");
        f = new Fake(); f.present = false; Fake missing = f;
        assertThatThrownBy(() -> missing.useCase().refresh(new RefreshTokenCommand("old")))
                .hasMessage("Refresh token not found or expired");
        assertThat(f.operations).containsExactly("verify", "lock", "rollback");
    }
    @Test void reuseRevocationCommitsBeforeExceptionForEveryNonCurrentState() {
        for (var state : new com.delivery.auth.domain.model.RefreshCredentialState[]{com.delivery.auth.domain.model.RefreshCredentialState.ROTATED, com.delivery.auth.domain.model.RefreshCredentialState.REVOKED}) {
            Fake f = new Fake(); f.state = state;
            assertThatThrownBy(() -> f.useCase().refresh(new RefreshTokenCommand("old")))
                    .isInstanceOf(RefreshCredentialReuse.class).hasMessage("Refresh token reuse detected; device session revoked");
            assertThat(f.operations).containsExactly("verify", "lock", "revoke", "commit");
            assertThat(f.inTransaction).isFalse();
        }
    }
    @Test void familyMismatchCommitsRevocationButMissingClaimKeepsLegacyCompatibility() {
        Fake f = new Fake(); f.claimedFamily = "other"; Fake mismatch = f;
        assertThatThrownBy(() -> mismatch.useCase().refresh(new RefreshTokenCommand("old")))
                .isInstanceOf(RefreshCredentialReuse.class).hasMessage("Refresh token family mismatch; device session revoked");
        assertThat(f.operations).containsExactly("verify", "lock", "family", "revoke", "commit");
        f = new Fake(); f.claimedFamily = null; f.useCase().refresh(new RefreshTokenCommand("old"));
        assertThat(f.operations).contains("commit").doesNotContain("revoke");
    }
    @Test void rejectsSessionAndAccountEligibilityWithoutMutation() {
        for (Boolean active : new Boolean[]{false, null}) {
            Fake f = new Fake(); f.sessionActive = active;
            assertThatThrownBy(() -> f.useCase().refresh(new RefreshTokenCommand("old"))).hasMessage("Session is inactive");
            assertThat(f.operations).doesNotContain("rotate", "revoke").contains("rollback");
        }
        for (LocalDateTime expiry : Arrays.asList(null, LocalDateTime.now().minusSeconds(1))) {
            Fake f = new Fake(); f.sessionExpiry = expiry;
            assertThatThrownBy(() -> f.useCase().refresh(new RefreshTokenCommand("old"))).hasMessage("Session is inactive");
        }
        for (Boolean active : new Boolean[]{false, null}) {
            Fake f = new Fake(); f.accountActive = active;
            assertThatThrownBy(() -> f.useCase().refresh(new RefreshTokenCommand("old"))).hasMessage("Account is blocked or inactive");
        }
        Fake f = new Fake(); f.verification = true; Fake unverified = f;
        assertThatThrownBy(() -> unverified.useCase().refresh(new RefreshTokenCommand("old"))).hasMessage("Email verification required");
        f = new Fake(); f.lifecycle = AuthAccount.LifecycleStatus.PENDING_PROFILE; Fake pending = f;
        assertThatThrownBy(() -> pending.useCase().refresh(new RefreshTokenCommand("old"))).isInstanceOf(CredentialsRejected.class).hasMessage("Account onboarding is not complete");
        f = new Fake(); f.userId = null; Fake unlinked = f;
        assertThatThrownBy(() -> unlinked.useCase().refresh(new RefreshTokenCommand("old"))).hasMessage("Account profile is not provisioned");
        f = new Fake(); f.verification = true; f.verified = LocalDateTime.now(); f.useCase().refresh(new RefreshTokenCommand("old"));
        assertThatThrownBy(() -> new Fake().useCase().refresh(null)).isInstanceOf(NullPointerException.class);
    }
    @Test void successorWriteFailureRollsBackAndLogoutIsIdempotentForMissingCredential() {
        Fake f = new Fake(); f.failRemember = true; Fake failing = f;
        assertThatThrownBy(() -> failing.useCase().refresh(new RefreshTokenCommand("old"))).hasMessage("storage failed");
        assertThat(f.operations).contains("rollback").doesNotContain("activity", "commit");
        f = new Fake(); f.useCase().logout("old");
        assertThat(f.operations).containsExactly("verify", "lock", "revoke", "commit");
        f = new Fake(); f.present = false; f.useCase().logout("old");
        assertThat(f.operations).containsExactly("verify", "lock", "commit");
        f = new Fake(); f.valid = false; Fake invalid = f;
        assertThatThrownBy(() -> invalid.useCase().logout("old")).hasMessage("Invalid refresh token");
    }
    static final class Fake implements AuthTransactionPort, RefreshTokenVerificationPort, RefreshCredentialLockPort, LockedRefreshCredential, SessionTokenPort {
        boolean valid = true, present = true, inTransaction, verification, failRemember;
        Boolean sessionActive = true, accountActive = true;
        Long userId = 11L;
        LocalDateTime sessionExpiry = LocalDateTime.now().plusDays(1), verified, rotatedAt, expiry;
        AuthAccount.LifecycleStatus lifecycle = AuthAccount.LifecycleStatus.ACTIVE;
        com.delivery.auth.domain.model.RefreshCredentialState state = com.delivery.auth.domain.model.RefreshCredentialState.CURRENT;
        String claimedFamily = "family";
        List<String> operations = new ArrayList<>();
        DefaultRefreshTokenUseCase useCase() { return new DefaultRefreshTokenUseCase(this,this,this,this); }
        public <T> T required(Supplier<T> operation) {
            inTransaction = true;
            try { T result = operation.get(); operations.add("commit"); return result; }
            catch (RuntimeException e) { operations.add("rollback"); throw e; }
            finally { inTransaction = false; }
        }
        public boolean isValid(String raw) { operations.add("verify"); return valid; }
        public String claimedFamily(String raw) { operations.add("family"); return claimedFamily; }
        public <T> T withLocked(String raw, Function<Optional<LockedRefreshCredential>,T> operation) {
            assertThat(inTransaction).isTrue(); operations.add("lock"); return operation.apply(present ? Optional.of(this) : Optional.empty());
        }
        public RefreshCredentialFacts facts() {
            return new RefreshCredentialFacts(state, new Session(2L,7L,"device",null,null,null,"family",sessionActive,null,sessionExpiry,null),
                new AuthAccount(7L,userId,lifecycle,1L,"a@example.com","hash",AuthAccount.Role.USER,accountActive,verification,verified,false,0L,null,null,0,null,false,null,null,0L,null,null,null));
        }
        public void revokeFamily(LocalDateTime at) { assertThat(inTransaction).isTrue(); operations.add("revoke"); }
        public void markRotated(LocalDateTime at) { operations.add("rotate"); rotatedAt = at; }
        public void rememberSuccessor(String raw, LocalDateTime issued, LocalDateTime expires) { operations.add("remember"); expiry = expires; if (failRemember) throw new IllegalStateException("storage failed"); }
        public void updateSessionActivity(LocalDateTime at, LocalDateTime expires) { operations.add("activity"); assertThat(at).isEqualTo(rotatedAt); assertThat(expires).isEqualTo(expiry); }
        public String issueAccessToken(AuthAccount a) { operations.add("access"); return "access"; }
        public String issueRefreshToken(AuthAccount a,String family) { operations.add("refresh:"+family); return "new"; }
    }
}
