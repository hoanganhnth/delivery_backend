package com.delivery.auth.application;

import com.delivery.auth.application.api.*;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.AuthAccount.LifecycleStatus;
import com.delivery.auth.domain.policy.AccountLifecyclePolicy;
import com.delivery.auth.domain.policy.AuthResourceMissing;
import java.time.LocalDateTime;

public final class DefaultIdentityProfileUseCase implements IdentityProfileUseCase {
    private final AuthTransactionPort transactions;
    private final AccountLifecyclePort accounts;
    private final IdentityInboxPort receipts;
    public DefaultIdentityProfileUseCase(AuthTransactionPort transactions, AccountLifecyclePort accounts, IdentityInboxPort receipts) {
        this.transactions=transactions;this.accounts=accounts;this.receipts=receipts;
    }
    @Override public void profileCreated(Event event, String fingerprint) {
        transactions.required(() -> {
            if (!"identity.profile.created".equals(event.eventType()) || event.principalId() == null
                    || event.profileId() == null || event.profileId() <= 0 || !"USER_PROFILE".equals(event.profileType())) {
                throw new IllegalArgumentException("Invalid identity.profile.created event");
            }
            var receipt=receipts.find(event.eventId()).orElse(null);
            if (receipt != null) {
                if (!receipt.eventType().equals(event.eventType()) || !receipt.principalId().equals(event.principalId())
                        || !receipt.fingerprint().equals(fingerprint)) {
                    throw new IllegalStateException("Conflicting identity event reuse");
                }
                return null;
            }
            AccountLifecyclePort.Change change;
            try {
                change=accounts.updateLocked(event.principalId(), account -> link(account,event.profileId()));
            } catch (AuthResourceMissing missing) {
                throw new IllegalArgumentException("Unknown principal in profile event");
            }
            AuthAccount linked=change.after();
            // BLOCKED snapshots must be replayed after the projection is created,
            // even if an earlier status event was ACKed before that projection existed.
            if (linked.lifecycleStatus() == LifecycleStatus.BLOCKED
                    || change.before().lifecycleStatus() != linked.lifecycleStatus()) {
                accounts.statusChanged(linked,null,"PROFILE_COMPLETED");
            }
            receipts.save(new IdentityInboxPort.Receipt(event.eventId(),event.eventType(),event.principalId(),fingerprint,LocalDateTime.now()));
            return null;
        });
    }
    private AuthAccount link(AuthAccount a, Long profileId) {
        if (a.userId() != null && !a.userId().equals(profileId)) {
            throw new IllegalStateException("Principal already linked to a different user profile");
        }
        LifecycleStatus target=AccountLifecyclePolicy.status(a.isActive(),profileId,a.emailVerificationRequired(),a.emailVerifiedAt());
        Long version=a.lifecycleVersion();
        if (a.lifecycleStatus() != target) version=(version == null ? 0L : version)+1L;
        return new AuthAccount(a.id(),profileId,target,version,a.email(),a.passwordHash(),a.role(),a.isActive(),
                a.emailVerificationRequired(),a.emailVerifiedAt(),a.userStatusSyncPending(),a.userStatusSyncVersion(),
                a.userStatusSyncAdminId(),a.userStatusSyncBlockReason(),a.userStatusSyncAttempts(),a.userStatusSyncLastError(),
                a.simulationActor(),a.simulationCohortId(),a.activeSimulationRunId(),a.simulationBindingVersion(),
                a.userStatusSyncUpdatedAt(),a.createdAt(),a.updatedAt());
    }
}
