package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.AuthAccount.LifecycleStatus;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AccountLifecycleWorkflowTest {
    @Test void blockCommitsIdentityAndRevocationBeforeProjectionAndIncrementsOnlyChangedLifecycle() {
        Fake f = new Fake(); f.account = account(1L, 7L, true, false, LifecycleStatus.ACTIVE, null, null, false);
        f.core(false).block(1L, 99L, "fraud review");
        assertThat(f.account.isActive()).isFalse();
        assertThat(f.account.lifecycleStatus()).isEqualTo(LifecycleStatus.BLOCKED);
        assertThat(f.account.lifecycleVersion()).isEqualTo(1L);
        assertThat(f.account.userStatusSyncPending()).isTrue();
        assertThat(f.account.userStatusSyncVersion()).isEqualTo(1L);
        assertThat(f.account.userStatusSyncAdminId()).isEqualTo(99L);
        assertThat(f.account.userStatusSyncBlockReason()).isEqualTo("fraud review");
        assertThat(f.account.userStatusSyncAttempts()).isZero();
        assertThat(f.account.userStatusSyncLastError()).isNull();
        assertThat(f.account.simulationCohortId()).isEqualTo(COHORT);
        assertThat(f.events).containsExactly("begin", "lock", "save", "revoke", "outbox:ADMIN_ACTION", "commit");
        f.afterCommit.run();
        assertThat(f.events).containsSubsequence("commit", "newTransaction", "projection:true:fraud review", "clear:1", "synchronized");
        f.core(false).block(1L, 100L, null);
        assertThat(f.account.lifecycleVersion()).isEqualTo(1L);
        assertThat(f.account.userStatusSyncVersion()).isEqualTo(2L);
        f.afterCommit.run();
        assertThat(f.events).contains("projection:true:Blocked by admin");
    }
    @Test void unblockingUsesOnboardingFactsAndNeverRestoresRevokedCredentials() {
        Fake f = new Fake();
        f.account = account(1L, null, false, true, LifecycleStatus.BLOCKED, 2L, 4L, false);
        f.core(false).unblock(1L, 99L);
        assertThat(f.account.lifecycleStatus()).isEqualTo(LifecycleStatus.PENDING_PROFILE);
        assertThat(f.account.userStatusSyncVersion()).isEqualTo(4L);
        assertThat(f.afterCommit).isNull();
        f.account = account(1L, 7L, false, true, LifecycleStatus.BLOCKED, 2L, 4L, false);
        f.core(false).unblock(1L, 99L);
        assertThat(f.account.lifecycleStatus()).isEqualTo(LifecycleStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(f.account.userStatusSyncBlockReason()).isNull();
        f.afterCommit.run(); assertThat(f.events).contains("projection:false:null");
        f.account = account(1L, 7L, false, false, LifecycleStatus.BLOCKED, 2L, 4L, false);
        f.core(false).unblock(1L, 99L);
        assertThat(f.account.lifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(f.events).doesNotContain("revoke");
    }
    @Test void eventModeRetainsOldPendingFactsWithoutSchedulingOrPollingHttp() {
        Fake f = new Fake(); f.account = account(1L, 7L, true, true, LifecycleStatus.ACTIVE, 8L, 9L, true);
        var core = f.core(true); core.block(1L, 99L, "new reason"); core.reconcile();
        assertThat(f.account.userStatusSyncVersion()).isEqualTo(9L);
        assertThat(f.account.userStatusSyncBlockReason()).isEqualTo("old reason");
        assertThat(f.afterCommit).isNull(); assertThat(f.limit).isZero();
        assertThat(f.events).contains("outbox:ADMIN_ACTION").doesNotContain("projection:true:new reason");
    }
    @Test void reconciliationCapsBatchSkipsInapplicableRowsAndContinuesFailures() {
        Fake f = new Fake();
        f.rows = List.of(account(1L, 7L, null, false, LifecycleStatus.BLOCKED, 2L, 4L, true),
                account(2L, 8L, true, false, LifecycleStatus.ACTIVE, 2L, 5L, true),
                account(3L, null, true, false, LifecycleStatus.ACTIVE, 2L, 6L, true),
                account(4L, 9L, true, false, LifecycleStatus.ACTIVE, 2L, 7L, false));
        f.failure = new IllegalStateException("x".repeat(501)); f.failUser = 7L;
        f.core(false).reconcile();
        assertThat(f.limit).isEqualTo(50); assertThat(f.failureMessage).hasSize(500);
        assertThat(f.events).containsSubsequence("failure:4", "logged:1", "projection:false:null", "clear:5");
        assertThat(f.events).doesNotContain("clear:6", "clear:7");
    }
    @Test void staleAcknowledgementDoesNotClearNewerWorkAndFailureMessagesKeepCompatibility() {
        Fake f = new Fake(); f.clear = 0;
        f.rows = List.of(account(1L, 7L, true, false, LifecycleStatus.ACTIVE, 2L, 4L, true));
        f.core(false).reconcile(); assertThat(f.events).doesNotContain("synchronized");
        for (String message : Arrays.asList(null, " ", "short failure")) {
            f.failure = new IllegalStateException(message); f.failUser = 7L;
            f.core(false).reconcile();
            assertThat(f.failureMessage).isEqualTo(message == null || message.isBlank() ? "IllegalStateException" : message);
        }
    }
    @Test void afterCommitReadsLatestIdentityAndSkipsMissingOrAlreadySynchronizedAccount() {
        Fake f = new Fake(); f.account = account(1L, 7L, true, false, LifecycleStatus.ACTIVE, 2L, 4L, false);
        f.core(false).block(1L, 99L, "reason"); f.account = null; f.afterCommit.run();
        assertThat(f.events).doesNotContain("newTransaction");
        f.account = account(1L, 7L, true, false, LifecycleStatus.ACTIVE, 2L, 4L, false);
        f.core(false).block(1L, 99L, "reason");
        f.account = account(1L, 7L, true, false, LifecycleStatus.ACTIVE, 2L, 5L, false);
        f.afterCommit.run(); assertThat(f.events).doesNotContain("projection:true:reason");
        f.account = account(1L, 7L, true, true, LifecycleStatus.BLOCKED, 2L, 5L, false);
        f.account = f.account.withVerifiedEmail(LocalDateTime.now());
        f.core(false).unblock(1L, 99L); assertThat(f.account.lifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
    }
    @Test void afterCommitFailureCommitsDiagnosticMetadataBeforeRethrowing() {
        Fake f = new Fake(); f.account=account(1L,7L,true,false,LifecycleStatus.ACTIVE,2L,4L,false);
        f.core(false).block(1L,99L,"reason");
        f.failUser=7L; f.failure=new IllegalStateException("User unavailable");
        assertThatThrownBy(() -> f.afterCommit.run()).isSameAs(f.failure);
        assertThat(f.events).containsSubsequence("commit","newTransaction","failure:5","newCommit");
        assertThat(f.events).doesNotContain("clear:5");
    }
    private static final UUID COHORT = UUID.randomUUID();
    static AuthAccount account(Long id, Long user, Boolean active, Boolean required, LifecycleStatus state,
            Long lifecycleVersion, Long syncVersion, Boolean pending) {
        return new AuthAccount(id, user, state, lifecycleVersion, "user@example.test", "hash", AuthAccount.Role.USER,
                active, required, null, pending, syncVersion, 4L, "old reason", 8, "old error", true, COHORT,
                UUID.randomUUID(), 11L, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
    }
    private static class Fake implements AuthAccountPort, AccountLifecyclePort, AuthTransactionPort,
            LifecycleTransactionPort, UserStatusProjectionPort, LifecycleTelemetryPort {
        AuthAccount account; List<AuthAccount> rows = List.of(); List<String> events = new ArrayList<>();
        Runnable afterCommit; int limit; int clear = 1; Long failUser; RuntimeException failure; String failureMessage;
        DefaultAccountLifecycleUseCase core(boolean enabled) { return new DefaultAccountLifecycleUseCase(this,this,this,this,this,this,enabled); }
        public <T> T required(Supplier<T> op) { events.add("begin"); T result=op.get(); events.add("commit"); return result; }
        public void afterCommit(Runnable action) { afterCommit=action; }
        public <T> T requiresNew(Supplier<T> action) { events.add("newTransaction"); T result=action.get(); events.add("newCommit"); return result; }
        public Optional<AuthAccount> findById(Long id) { return Optional.ofNullable(account); }
        public Optional<AuthAccount> findByEmail(String email) { throw new UnsupportedOperationException(); }
        public AuthAccount save(AuthAccount a) { throw new UnsupportedOperationException(); }
        public AuthAccount createOrResume(AuthAccount a, Consumer<AuthAccount> winner) { throw new UnsupportedOperationException(); }
        public Change updateLocked(Long id, UnaryOperator<AuthAccount> op) { events.add("lock"); var before=account; account=op.apply(account); events.add("save"); return new Change(before,account); }
        public List<AuthAccount> pending(int limit) { this.limit=limit; return rows; }
        public int clearPending(Long id, Long version, LocalDateTime at) { events.add("clear:"+version); return clear; }
        public void recordFailure(Long id, Long version, String message, LocalDateTime at) { failureMessage=message; events.add("failure:"+version); }
        public void revokeCredentials(Long id, LocalDateTime at) { events.add("revoke"); }
        public void statusChanged(AuthAccount a, Long adminId, String reason) { events.add("outbox:"+reason); }
        public void synchronize(Long user, Long adminId, String reason, boolean blocked) { events.add("projection:"+blocked+":"+reason); if (user.equals(failUser)) throw failure; }
        public void synchronizedStatus(Long userId, boolean blocked) { events.add("synchronized"); }
        public void reconciliationFailed(Long accountId, RuntimeException failure) { events.add("logged:"+accountId); }
    }
}
