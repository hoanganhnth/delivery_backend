package com.delivery.auth_service.service;

import com.delivery.auth.application.api.AuthAccountPort;
import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth_service.repository.AuthAccountRepository;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class JpaAuthAccountAdapter implements AuthAccountPort {
    private final AuthAccountRepository accounts;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public JpaAuthAccountAdapter(AuthAccountRepository accounts) { this(accounts, null); }

    @org.springframework.beans.factory.annotation.Autowired
    public JpaAuthAccountAdapter(AuthAccountRepository accounts, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.jdbc = jdbc;
    }
    @Override public Optional<AuthAccount> findByEmail(String email) {
        return accounts.findByEmail(email).map(AuthAccountMapping::toDomain);
    }
    @Override public Optional<AuthAccount> findById(Long id) {
        return accounts.findById(id).map(AuthAccountMapping::toDomain);
    }
    @Override public AuthAccount save(AuthAccount account) {
        var entity = account.id() == null ? new com.delivery.auth_service.entity.AuthAccount()
                : accounts.findById(account.id()).orElseThrow(() -> new IllegalStateException("Auth account disappeared"));
        AuthAccountMapping.apply(account, entity);
        return AuthAccountMapping.toDomain(accounts.save(entity));
    }
    @Override public AuthAccount createOrResume(AuthAccount account, Consumer<AuthAccount> verifyWinner) {
        if (jdbc != null && org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            return insertWithinTransaction(account, verifyWinner);
        }
        // Without an outer transaction, repository rollback completes before winner lookup.
        try { return save(account); }
        catch (DataIntegrityViolationException race) {
            var winner = findByEmail(account.email()).orElseThrow(() -> race);
            verifyWinner.accept(winner);
            return winner;
        }
    }
    private AuthAccount insertWithinTransaction(AuthAccount a, Consumer<AuthAccount> verifyWinner) {
        // ON CONFLICT keeps both PostgreSQL and Hibernate usable in the social transaction.
        // All account facts are persisted; policy for the winning identity stays in core.
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int inserted = jdbc.update("""
                INSERT INTO auth_account (user_id, lifecycle_status, lifecycle_version, email,
                    password_hash, role, is_active, email_verification_required, email_verified_at,
                    user_status_sync_pending, user_status_sync_version, user_status_sync_admin_id,
                    user_status_sync_block_reason, user_status_sync_attempts, user_status_sync_last_error,
                    simulation_actor, simulation_cohort_id, active_simulation_run_id,
                    simulation_binding_version, user_status_sync_updated_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, a.userId(), a.lifecycleStatus().name(), a.lifecycleVersion(), a.email(), a.passwordHash(),
                a.role().name(), a.isActive(), a.emailVerificationRequired(), a.emailVerifiedAt(),
                a.userStatusSyncPending(), a.userStatusSyncVersion(), a.userStatusSyncAdminId(),
                a.userStatusSyncBlockReason(), a.userStatusSyncAttempts(), a.userStatusSyncLastError(),
                a.simulationActor(), a.simulationCohortId(), a.activeSimulationRunId(), a.simulationBindingVersion(),
                a.userStatusSyncUpdatedAt(), now, now);
        AuthAccount saved = findByEmail(a.email()).orElseThrow(() -> new IllegalStateException("Auth identity insert returned no account"));
        if (inserted == 0) verifyWinner.accept(saved);
        return saved;
    }

}
