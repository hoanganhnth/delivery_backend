package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.AuthAccount.LifecycleStatus;
import java.time.LocalDateTime;

public final class DefaultAccountLifecycleUseCase implements AccountLifecycleUseCase {
    private final AuthAccountPort accounts;
    private final AccountLifecyclePort persistence;
    private final AuthTransactionPort transactions;
    private final LifecycleTransactionPort callbacks;
    private final UserStatusProjectionPort projection;
    private final LifecycleTelemetryPort telemetry;
    private final boolean eventsEnabled;
    public DefaultAccountLifecycleUseCase(AuthAccountPort accounts, AccountLifecyclePort persistence,
            AuthTransactionPort transactions, LifecycleTransactionPort callbacks,
            UserStatusProjectionPort projection, LifecycleTelemetryPort telemetry, boolean eventsEnabled) {
        this.accounts = accounts; this.persistence = persistence; this.transactions = transactions;
        this.callbacks = callbacks; this.projection = projection; this.telemetry = telemetry;
        this.eventsEnabled = eventsEnabled;
    }
    @Override public void block(Long id, Long adminId, String reason) { change(id, adminId, reason, false); }
    @Override public void unblock(Long id, Long adminId) { change(id, adminId, null, true); }
    private void change(Long id, Long adminId, String reason, boolean active) {
        transactions.required(() -> {
            AuthAccount account = persistence.updateLocked(id, current -> changed(current, adminId, reason, active)).after();
            if (!active) persistence.revokeCredentials(id, LocalDateTime.now());
            persistence.statusChanged(account, adminId, "ADMIN_ACTION");
            if (!eventsEnabled && account.userId() != null) {
                callbacks.afterCommit(() -> accounts.findById(id).ifPresent(snapshot ->
                        synchronizeInNewTransaction(snapshot)));
            }
            return null;
        });
    }
    private AuthAccount changed(AuthAccount a, Long adminId, String reason, boolean active) {
        LifecycleStatus target = com.delivery.auth.domain.policy.AccountLifecyclePolicy.status(
                active, a.userId(), a.emailVerificationRequired(), a.emailVerifiedAt());
        boolean pending = !eventsEnabled && a.userId() != null;
        Long lifecycleVersion = a.lifecycleVersion();
        if (a.lifecycleStatus() != target) lifecycleVersion = (lifecycleVersion == null ? 0L : lifecycleVersion) + 1L;
        return new AuthAccount(a.id(), a.userId(), target,
                lifecycleVersion,
                a.email(), a.passwordHash(), a.role(), active, a.emailVerificationRequired(), a.emailVerifiedAt(),
                pending ? Boolean.TRUE : a.userStatusSyncPending(),
                pending ? Long.valueOf((a.userStatusSyncVersion() == null ? 0L : a.userStatusSyncVersion()) + 1L) : a.userStatusSyncVersion(),
                pending ? adminId : a.userStatusSyncAdminId(),
                pending ? (active ? null : reason) : a.userStatusSyncBlockReason(),
                pending ? Integer.valueOf(0) : a.userStatusSyncAttempts(), pending ? null : a.userStatusSyncLastError(),
                a.simulationActor(), a.simulationCohortId(), a.activeSimulationRunId(), a.simulationBindingVersion(),
                pending ? LocalDateTime.now() : a.userStatusSyncUpdatedAt(), a.createdAt(), a.updatedAt());
    }
    @Override public void reconcile() {
        if (eventsEnabled) return;
        for (AuthAccount account : persistence.pending(50)) {
            try { synchronize(account); }
            catch (RuntimeException failure) { telemetry.reconciliationFailed(account.id(), failure); }
        }
    }
    private void synchronizeInNewTransaction(AuthAccount account) {
        // Commit the failure metadata before rethrowing the post-commit transport error.
        RuntimeException failure = callbacks.requiresNew(() -> {
            try { synchronize(account); return null; }
            catch (RuntimeException rejected) { return rejected; }
        });
        if (failure != null) throw failure;
    }
    private void synchronize(AuthAccount a) {
        if (!Boolean.TRUE.equals(a.userStatusSyncPending()) || a.userId() == null) return;
        boolean blocked = !Boolean.TRUE.equals(a.isActive());
        try {
            projection.synchronize(a.userId(), a.userStatusSyncAdminId(),
                    blocked ? (a.userStatusSyncBlockReason() == null ? "Blocked by admin" : a.userStatusSyncBlockReason()) : null,
                    blocked);
            if (persistence.clearPending(a.id(), a.userStatusSyncVersion(), LocalDateTime.now()) == 1) {
                telemetry.synchronizedStatus(a.userId(), blocked);
            }
        } catch (RuntimeException failure) {
            String message = failure.getMessage();
            if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
            persistence.recordFailure(a.id(), a.userStatusSyncVersion(),
                    message.length() <= 500 ? message : message.substring(0, 500), LocalDateTime.now());
            throw failure;
        }
    }
}
