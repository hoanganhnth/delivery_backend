package com.delivery.auth_service.service;

import com.delivery.auth.application.api.AccountLifecyclePort;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.policy.AuthResourceMissing;
import com.delivery.auth_service.entity.RefreshTokenRecord;
import com.delivery.auth_service.repository.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaAccountLifecycleAdapter implements AccountLifecyclePort {
    private final AuthAccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final RefreshTokenRecordRepository refresh;
    private final IdentityStatusOutboxService outbox;
    @Override public Change updateLocked(Long id, UnaryOperator<AuthAccount> update) {
        var entity = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new AuthResourceMissing("Account not found with id: " + id));
        var before = AuthAccountMapping.toDomain(entity);
        var changed = update.apply(before);
        AuthAccountMapping.apply(changed, entity);
        accounts.save(entity);
        return new Change(before, changed);
    }
    @Override public List<AuthAccount> pending(int limit) {
        return accounts.findPendingUserStatusSync(PageRequest.of(0, limit)).stream().map(AuthAccountMapping::toDomain).toList();
    }
    @Override public int clearPending(Long id, Long version, LocalDateTime at) {
        return accounts.clearUserStatusSyncPending(id, version, at);
    }
    @Override public void recordFailure(Long id, Long version, String message, LocalDateTime at) {
        accounts.recordUserStatusSyncFailure(id, version, message, at);
    }
    @Override public void revokeCredentials(Long id, LocalDateTime at) {
        refresh.revokeAccount(id, RefreshTokenRecord.State.REVOKED, at);
        sessions.deactivateAllActiveSessions(id, at);
    }
    @Override public void statusChanged(AuthAccount account, Long adminId, String reasonCode) {
        // Event transport accepts an explicit snapshot, avoiding a second identity lookup.
        outbox.statusChanged(account.id(), com.delivery.identity.contracts.IdentityLifecycleStatus.valueOf(account.lifecycleStatus().name()),
                account.lifecycleVersion(), adminId, reasonCode);
    }
}
